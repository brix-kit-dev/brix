/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.errors.SslAuthenticationException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.test.KafkaClusterTestKit;
import org.apache.kafka.common.test.TestKitNodes;
import org.apache.kafka.common.utils.Exit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.infra.adapter.kafka.config.KafkaAutoConfiguration;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxTransport;

/**
 * The adapter's clients reach a broker that requires TLS and a client certificate with the settings the host passes
 * through, and a client without a certificate is turned away (root BRX-RS-SECURITY-006; Kafka design D35, §14 K24).
 * The certificates are made for the test by the JDK's keytool.
 */
class KafkaMutualTlsTest {

    private static final String PASSWORD = "brix-test-" + UUID.randomUUID();

    @TempDir
    static Path certificates;

    private static KafkaClusterTestKit cluster;

    @BeforeAll
    static void startBrokerThatRequiresClientCertificates() throws Exception {
        Exit.setExitProcedure((status, message) -> {
            throw new IllegalStateException("Kafka asked to exit: " + message);
        });
        Exit.setHaltProcedure((status, message) -> {
            throw new IllegalStateException("Kafka asked to halt: " + message);
        });
        Path ca = keytool("-genkeypair", "-alias", "ca", "-dname", "CN=brix-test-ca", "-ext", "bc:c",
            "-keystore", "ca.p12");
        keytool("-exportcert", "-alias", "ca", "-rfc", "-file", "ca.pem", "-keystore", ca.toString());
        keytool("-importcert", "-alias", "ca", "-file", "ca.pem", "-noprompt", "-keystore", "trust.p12");
        signed("broker", "SAN=dns:localhost,ip:127.0.0.1");
        signed("client", null);
        TestKitNodes nodes = new TestKitNodes.Builder()
            .setCombined(true)
            .setNumBrokerNodes(1)
            .setNumControllerNodes(1)
            .build();
        KafkaClusterTestKit.Builder builder = new KafkaClusterTestKit.Builder(nodes);
        // The test kit builds PLAINTEXT listeners only; its broker listener is switched to TLS here.
        Map<String, String> broker = Map.ofEntries(
            Map.entry("listener.security.protocol.map", "EXTERNAL:SSL,CONTROLLER:PLAINTEXT"),
            Map.entry("ssl.keystore.type", "PKCS12"),
            Map.entry("ssl.keystore.location", certificates.resolve("broker.p12").toString()),
            Map.entry("ssl.keystore.password", PASSWORD),
            Map.entry("ssl.key.password", PASSWORD),
            Map.entry("ssl.truststore.type", "PKCS12"),
            Map.entry("ssl.truststore.location", certificates.resolve("trust.p12").toString()),
            Map.entry("ssl.truststore.password", PASSWORD),
            Map.entry("ssl.client.auth", "required"),
            Map.entry("auto.create.topics.enable", "false"),
            Map.entry("offsets.topic.replication.factor", "1"),
            Map.entry("transaction.state.log.replication.factor", "1"),
            Map.entry("transaction.state.log.min.isr", "1"),
            Map.entry("group.initial.rebalance.delay.ms", "0"));
        broker.forEach(builder::setConfigProp);
        cluster = builder.build();
        cluster.format();
        cluster.startup();
        cluster.waitForReadyBrokers();
    }

    @AfterAll
    static void stopBroker() throws Exception {
        if (cluster != null) {
            cluster.close();
        }
        Exit.resetExitProcedure();
        Exit.resetHaltProcedure();
    }

    @Test
    void theAdaptersAdminProducerAndConsumersUseTheClientCertificate() throws Exception {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
            .withPropertyValues(withClientCertificate("brix.infra.kafka.client."))
            .withPropertyValues(
                "brix.infra.kafka.bootstrap-servers=" + cluster.bootstrapServers(),
                "brix.infra.kafka.topic-prefix=tls-",
                "brix.outbox.relay.transport=kafka")
            .run(context -> {
                assertThat(context).hasNotFailed();
                KafkaClientSettings client = context.getBean(KafkaClientSettings.class);
                assertThat(client.securityProtocol()).isEqualTo("SSL");

                context.getBean(OutboxTransport.class).publish(event("event-tls-1"));

                assertThat(read(client.configs(), "tls-brix.event.MemberAccepted"))
                    .singleElement().satisfies(value -> assertThat(value).contains("event-tls-1"));
                try (Admin admin = Admin.create(client.configs())) {
                    Config topic = admin.describeConfigs(List.of(new ConfigResource(ConfigResource.Type.TOPIC,
                        "tls-brix.event.MemberAccepted"))).all().get(10, TimeUnit.SECONDS).values().iterator().next();
                    assertThat(topic.get(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG).value())
                        .as("set when the adapter created the topic").isEqualTo("1");
                }
            });
    }

    @Test
    void aClientWithoutACertificateIsTurnedAway() {
        Map<String, String> serverOnly = new HashMap<>(clientCertificate());
        serverOnly.keySet().removeIf(name -> name.startsWith("ssl.keystore") || name.equals("ssl.key.password"));
        KafkaClientSettings client = KafkaClientSettings.of(cluster.bootstrapServers(), serverOnly,
            KafkaEventSettings.defaults(), false);

        try (Admin admin = Admin.create(client.configs())) {
            assertThatThrownBy(() -> admin.describeCluster().clusterId().get(20, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(SslAuthenticationException.class);
        }
    }

    private static List<String> read(Map<String, Object> client, String topic) {
        Map<String, Object> configs = new HashMap<>(client);
        configs.put(ConsumerConfig.GROUP_ID_CONFIG, "tls-reader-" + UUID.randomUUID());
        configs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configs.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<String> values = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(configs)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (values.isEmpty() && System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    values.add(record.value());
                }
            }
        }
        return values;
    }

    private static String[] withClientCertificate(String prefix) {
        return clientCertificate().entrySet().stream()
            .map(setting -> prefix + setting.getKey() + "=" + setting.getValue())
            .toArray(String[]::new);
    }

    private static Map<String, String> clientCertificate() {
        return Map.of(
            "security.protocol", "SSL",
            "ssl.keystore.type", "PKCS12",
            "ssl.keystore.location", certificates.resolve("client.p12").toString(),
            "ssl.keystore.password", PASSWORD,
            "ssl.key.password", PASSWORD,
            "ssl.truststore.type", "PKCS12",
            "ssl.truststore.location", certificates.resolve("trust.p12").toString(),
            "ssl.truststore.password", PASSWORD);
    }

    private static CanonicalOutboxMessage event(String eventId) {
        return new CanonicalOutboxMessage(eventId, eventId, "EVENT", "MemberAccepted", "1.0.0", "CRITICAL",
            "tls-publisher", "TENANT", 910001L, "member-1", OffsetDateTime.parse("2026-10-30T03:00:00Z"),
            "correlation-" + eventId, null, null, null, "{\"memberId\":1}", 1);
    }

    /** A key pair signed by the test CA, in {@code <name>.p12} with the CA's certificate. */
    private static void signed(String name, String subjectAlternativeNames) throws Exception {
        List<String> generate = new ArrayList<>(List.of("-genkeypair", "-alias", name, "-dname", "CN=" + name,
            "-keystore", name + ".p12"));
        List<String> sign = new ArrayList<>(List.of("-gencert", "-alias", "ca", "-keystore", "ca.p12", "-rfc",
            "-infile", name + ".csr", "-outfile", name + ".pem", "-ext", "KU=digitalSignature,keyEncipherment",
            "-ext", "EKU=serverAuth,clientAuth"));
        if (subjectAlternativeNames != null) {
            generate.addAll(List.of("-ext", subjectAlternativeNames));
            sign.addAll(List.of("-ext", subjectAlternativeNames));
        }
        keytool(generate.toArray(String[]::new));
        keytool("-certreq", "-alias", name, "-file", name + ".csr", "-keystore", name + ".p12");
        keytool(sign.toArray(String[]::new));
        keytool("-importcert", "-alias", "ca", "-file", "ca.pem", "-noprompt", "-keystore", name + ".p12");
        keytool("-importcert", "-alias", name, "-file", name + ".pem", "-keystore", name + ".p12");
    }

    private static Path keytool(String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "keytool")
            .toString()));
        command.addAll(List.of(arguments));
        command.addAll(List.of("-storetype", "PKCS12", "-storepass", PASSWORD, "-keypass", PASSWORD, "-keyalg", "RSA",
            "-keysize", "2048", "-validity", "2"));
        Process process = new ProcessBuilder(clean(command)).directory(certificates.toFile()).redirectErrorStream(true)
            .start();
        String output = new String(process.getInputStream().readAllBytes());
        if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("keytool " + String.join(" ", arguments) + " failed: " + output);
        }
        int keystore = command.indexOf("-keystore");
        return certificates.resolve(command.get(keystore + 1));
    }

    /** Leaves out the options keytool rejects for a command. */
    private static List<String> clean(List<String> command) {
        String action = command.get(1);
        List<String> keyOptions = List.of("-keyalg", "-keysize");
        List<String> cleaned = new ArrayList<>();
        for (int i = 0; i < command.size(); i++) {
            String option = command.get(i);
            boolean keyOnly = keyOptions.contains(option) && !"-genkeypair".equals(action);
            boolean validity = "-validity".equals(option) && !"-genkeypair".equals(action) && !"-gencert".equals(action);
            boolean keypass = "-keypass".equals(option) && !"-genkeypair".equals(action);
            if (keyOnly || validity || keypass) {
                i++;
                continue;
            }
            cleaned.add(option);
        }
        if (!Files.exists(Path.of(cleaned.get(0)))) {
            throw new IllegalStateException("no keytool at " + cleaned.get(0));
        }
        return cleaned;
    }
}
