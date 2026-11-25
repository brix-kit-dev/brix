/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.brix.architecture.guard.profiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import io.brix.architecture.guard.testsupport.CompiledModule;
import io.brix.samples.fixturealpha.core.AlphaPrice;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves that each library rule of the plugin profile names the package the library really has: it refuses a module
 * that uses the library's classes by their real names. A rule that names the Maven group, as the OkHttp rule once did
 * ({@code com.squareup.okhttp3}), checks nothing and fails here.
 */
class PluginProfileLibrariesTest {

    private static final String MODULE_CLASS = "io.brix.samples.fixtureplugin.server.UsesLibrary";

    /** Every library rule of the profile and real class names of the library. */
    private static final Map<ArchRule, List<String>> LIBRARIES = new LinkedHashMap<>();

    static {
        LIBRARIES.put(PluginProfile.noSpringKafka, List.of("org.springframework.kafka.core.KafkaTemplate",
            "org.springframework.kafka.annotation.KafkaListener"));
        LIBRARIES.put(PluginProfile.noApacheKafka, List.of("org.apache.kafka.clients.producer.KafkaProducer",
            "org.apache.kafka.clients.admin.Admin"));
        LIBRARIES.put(PluginProfile.noSpringDataRedis, List.of("org.springframework.data.redis.core.StringRedisTemplate"));
        LIBRARIES.put(PluginProfile.noJedis, List.of("redis.clients.jedis.Jedis"));
        LIBRARIES.put(PluginProfile.noLettuce, List.of("io.lettuce.core.RedisClient"));
        LIBRARIES.put(PluginProfile.noSpringAmqp, List.of("org.springframework.amqp.core.AmqpTemplate",
            "org.springframework.amqp.rabbit.annotation.RabbitListener"));
        LIBRARIES.put(PluginProfile.noRestTemplate, List.of("org.springframework.web.client.RestTemplate",
            "org.springframework.web.client.RestClient"));
        LIBRARIES.put(PluginProfile.noWebClient, List.of("org.springframework.web.reactive.function.client.WebClient"));
        LIBRARIES.put(PluginProfile.noOpenFeign, List.of("org.springframework.cloud.openfeign.FeignClient"));
        LIBRARIES.put(PluginProfile.noOkHttp, List.of("okhttp3.OkHttpClient", "okhttp3.Request"));
        LIBRARIES.put(PluginProfile.noDirectJwtUsage, List.of("io.jsonwebtoken.Jwts", "com.nimbusds.jwt.SignedJWT",
            "com.nimbusds.jose.JWSObject", "com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet",
            "org.jose4j.jwt.consumer.JwtConsumer", "org.springframework.security.oauth2.jwt.NimbusJwtDecoder",
            "org.springframework.security.oauth2.jose.jws.SignatureAlgorithm",
            "org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken"));
        LIBRARIES.put(PluginProfile.noDirectAuth0Usage, List.of("com.auth0.jwt.JWT", "com.auth0.jwk.JwkProvider"));
        LIBRARIES.put(PluginProfile.noDirectCryptoUsage,
            List.of("org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder"));
        LIBRARIES.put(PluginProfile.noDirectOAuth2ClientUsage,
            List.of("org.springframework.security.oauth2.client.OAuth2AuthorizedClient"));
        LIBRARIES.put(PluginProfile.noDirectKafkaTemplateUsage, List.of("org.springframework.kafka.core.KafkaTemplate"));
        LIBRARIES.put(PluginProfile.noDirectRabbitTemplateUsage,
            List.of("org.springframework.amqp.rabbit.core.RabbitTemplate"));
        LIBRARIES.put(PluginProfile.noDirectKafkaProducerUsage, List.of("org.apache.kafka.clients.producer.KafkaProducer"));
        LIBRARIES.put(PluginProfile.noDirectStreamBridgeUsage,
            List.of("org.springframework.cloud.stream.function.StreamBridge"));
        LIBRARIES.put(PluginProfile.noDirectJmsTemplateUsage, List.of("org.springframework.jms.core.JmsTemplate"));
        LIBRARIES.put(PluginProfile.noBrokerSdkDependencies, List.of("org.apache.kafka.clients.consumer.KafkaConsumer",
            "org.springframework.kafka.listener.MessageListener", "com.rabbitmq.client.Channel",
            "org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer",
            "org.springframework.cloud.stream.messaging.Source", "org.springframework.jms.annotation.JmsListener",
            "org.springframework.jms.listener.DefaultMessageListenerContainer", "jakarta.jms.Session",
            "javax.jms.Session"));
    }

    @Test
    @DisplayName("Each library rule refuses a module that uses the library by its real class names")
    void eachLibraryRuleKnowsTheRealPackage(@TempDir Path directory) throws IOException {
        for (Map.Entry<ArchRule, List<String>> rule : LIBRARIES.entrySet()) {
            for (String library : rule.getValue()) {
                JavaClasses module = CompiledModule.using(Files.createTempDirectory(directory, "module"), MODULE_CLASS,
                    library);

                AssertionError error = assertThrows(AssertionError.class, () -> rule.getKey().check(module),
                    library + " passes: " + rule.getKey().getDescription());
                assertTrue(error.getMessage().contains(library), error.getMessage());
            }
        }
    }

    @Test
    @DisplayName("The JDK's HTTP client is refused by its real package too")
    void jdkHttpClientIsRefused() {
        JavaClasses module = new ClassFileImporter().importClasses(UsesJdkHttpClient.class);

        AssertionError error = assertThrows(AssertionError.class, () -> PluginProfile.noJdkHttpClient.check(module));
        assertTrue(error.getMessage().contains(HttpClient.class.getName()), error.getMessage());
    }

    @Test
    @DisplayName("A module that uses only the JDK passes every library rule")
    void jdkOnlyPasses() {
        JavaClasses module = new ClassFileImporter().importClasses(AlphaPrice.class);

        assertEquals(20, LIBRARIES.size());
        LIBRARIES.keySet().forEach(rule -> assertDoesNotThrow(() -> rule.check(module), rule.getDescription()));
    }

    /** A plugin class that opens the JDK's HTTP client: forbidden. */
    private static final class UsesJdkHttpClient {

        HttpClient client() {
            return HttpClient.newHttpClient();
        }
    }
}
