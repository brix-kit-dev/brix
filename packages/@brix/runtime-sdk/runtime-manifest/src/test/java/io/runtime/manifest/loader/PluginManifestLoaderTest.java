/*
 * Copyright 2026 Runtime SDK Authors
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
package io.runtime.manifest.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.runtime.manifest.validation.ManifestValidationException;

class PluginManifestLoaderTest {

    private final PluginManifestLoader loader = new PluginManifestLoader();

    @Test
    void loadsStrictV3010YamlManifest() {
        var manifest = loader.loadFromString(validManifest());

        assertThat(manifest.pluginId()).isEqualTo("sample-plugin");
        assertThat(manifest.getCapabilities().getRequired())
            .extracting("id")
            .containsExactly("AuthCapability");
    }

    @Test
    void artifactWithoutActiveManifestHasNoManifest() {
        assertThat(loader.findActiveFromClasspath(PluginManifestLoaderTest.class)).isEmpty();
        assertThat(loader.findActiveFromClasspath(String.class)).isEmpty();
    }

    @Test
    void rejectsUnknownFields() {
        assertThatThrownBy(() -> loader.loadFromString(validManifest() + "unexpected: value\n"))
            .isInstanceOf(ManifestLoadException.class)
            .hasMessageContaining("Failed to parse plugin manifest YAML");
    }

    @Test
    void rejectsSecretConfigDefaults() {
        String yaml = validManifest()
            + "config:\n"
            + "  - key: plugin.secret\n"
            + "    type: string\n"
            + "    sensitivity: secret\n"
            + "    default: visible\n";

        assertThatThrownBy(() -> loader.loadFromString(yaml))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("Secret config entries must not declare defaults");
    }

    @Test
    void loadsReliableEventPublishDeclaration() {
        var manifest = loader.loadFromString(reliablePublisherManifest());

        assertThat(manifest.getEvents().getPublishes())
            .extracting("eventType")
            .containsExactly("TenantFirstOwnerAccepted");
        assertThat(manifest.getEvents().getPublishes())
            .extracting("schemaRef")
            .containsExactly("brix://schemas/platform-tenant/tenant-first-owner-accepted/1.0.0");
        assertThat(manifest.getEvents().getPublishes().get(0).getReliability())
            .isEqualTo("CRITICAL");
    }

    @Test
    void loadsEndpointProvidesDeclaration() {
        var manifest = loader.loadFromString(templateEndpointManifest());

        assertThat(manifest.endpointDeclarations())
            .extracting("endpointId")
            .containsExactly("app-booking.template.status.v1");
        assertThat(manifest.endpointDeclarations().get(0).getHandlerId())
            .isEqualTo("app-booking.template.status.v1");
    }

    @Test
    void rejectsPublishedEventWithoutReliability() {
        assertThatThrownBy(() -> loader.loadFromString(reliablePublisherManifest()
                .replace("      reliability: CRITICAL\n", "")))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("events.publishes[0].reliability");
    }

    @Test
    void rejectsReliablePublishedEventWithoutOutbox() {
        assertThatThrownBy(() -> loader.loadFromString(reliablePublisherManifest()
                .replace("  outbox: platform_tenant_outbox\n", "")))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("CRITICAL/STANDARD event publishers must declare data.outbox");
    }

    @Test
    void rejectsSubscribedEventWithoutInbox() {
        assertThatThrownBy(() -> loader.loadFromString(subscriberManifest()
                .replace("  inbox: platform_tenant_inbox\n", "")))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("must declare data.inbox");
    }

    @Test
    void anOutboxNamedOtherwiseThanItsStorageIsRefused() {
        // Root BRX-RS-MESSAGE-023: canonical tables are named after the storage id.
        assertThatThrownBy(() -> loader.loadFromString(subscriberManifest()
                .replace("inbox: platform_tenant_inbox", "inbox: tenant_events")))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("the inbox must be named platform_tenant_inbox");
    }

    @Test
    void aSubscriberThatKeepsAnInboxRequiresPersistence() {
        // Root BRX-RS-MESSAGE-025: the inbox record and the handler's effects share the Owner's transaction.
        assertThatThrownBy(() -> loader.loadFromString(subscriberManifest()
                .replace("io.runtime.sdk.capability.PersistenceCapability", "io.runtime.sdk.capability.CacheCapability")))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("must require PersistenceCapability");
    }

    @Test
    void aCommandProviderIsNamedByItsCommandType() {
        // Kafka design §14 K22: commands are keyed by type, as the bundled JSON schema has them.
        assertThatCode(() -> loader.loadFromString(commandProviderManifest())).doesNotThrowAnyException();
    }

    @Test
    void aCommandProviderKeepsAnInbox() {
        assertThatThrownBy(() -> loader.loadFromString(commandProviderManifest()
                .replace("  inbox: platform_tenant_inbox\n", "")))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("Command providers must declare data.inbox");
    }

    @Test
    void aCommandConsumerWritesToItsOutbox() {
        assertThatThrownBy(() -> loader.loadFromString(commandProviderManifest()
                .replace("  outbox: platform_tenant_outbox\n", "")
                .replace("  consumes: []\nevents:", """
                  consumes:
                    - commandType: ShipOrder
                      versionRange: ">=1.0.0 <2.0.0"
                      required: true
                      timeoutPolicyRef: event-standard
                      retryPolicyRef: event-standard
                      idempotencyPolicyRef: persistent-inbox
                events:""")))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("Command consumers must declare data.outbox");
    }

    @Test
    void aQueryProviderIsNamedByItsPluginAndCarriesOneContractVersion() {
        // Query design §4.1 (root BRX-RS-MANIFEST-025): the bundled schema has no version field; both schemas carry it.
        assertThatCode(() -> loader.loadFromString(queryProviderManifest())).doesNotThrowAnyException();
        assertThatThrownBy(() -> loader.loadFromString(queryProviderManifest()
                .replace("queryType: sample-plugin.find-thing", "queryType: other-plugin.find-thing")))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("A provided query is named <pluginId>.<name>");
        assertThatThrownBy(() -> loader.loadFromString(queryProviderManifest()
                .replace("sample-plugin/thing/1.0.0", "sample-plugin/thing/1.1.0")))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("so they must be the same");
    }

    @Test
    void aQueryConsumerDeclaresTheQueryCapabilityAndNotItsOwnQueries() {
        String consumer = validManifest().replace("queries:\n  provides: []\n  consumes: []\n", """
            queries:
              provides: []
              consumes:
                - queryType: other-plugin.find-thing
                  versionRange: ">=1.0.0 <2.0.0"
                  required: false
                  timeoutPolicyRef: query-interactive
                  retryPolicyRef: query-no-retry
            """);
        assertThatThrownBy(() -> loader.loadFromString(consumer))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("Query consumers must declare io.runtime.sdk.capability.QueryCapability");
        String declared = consumer.replace("capabilities:\n  required:\n",
            "capabilities:\n  required:\n    - id: io.runtime.sdk.capability.QueryCapability\n"
                + "      version: \">=3.3.0 <4.0.0\"\n");
        assertThatCode(() -> loader.loadFromString(declared)).doesNotThrowAnyException();
        assertThatThrownBy(() -> loader.loadFromString(declared.replace("other-plugin.find-thing",
                "sample-plugin.find-thing")))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("not through its own query");
    }

    @Test
    void aSubscriberThatKeepsNoInboxNeedsNoInboxTable() {
        // Root BRX-RS-MANIFEST-025, Kafka design §13 K3: no-durable-idempotency subscriptions keep no inbox.
        assertThatCode(() -> loader.loadFromString(subscriberManifest()
                .replace("  inbox: platform_tenant_inbox\n", "")
                .replace("idempotencyPolicyRef: persistent-inbox", "idempotencyPolicyRef: no-durable-idempotency")))
            .doesNotThrowAnyException();
    }

    @Test
    void aCommandProviderNamesEveryPolicy() {
        // Root BRX-RS-MANIFEST-025 (standard-link plan L4.3): scope, access, timeout, concurrency, retry, idempotency.
        for (String policy : java.util.List.of("scopePolicyRef", "accessPolicyRef", "timeoutPolicyRef",
                "concurrencyPolicyRef", "retryPolicyRef", "idempotencyPolicyRef")) {
            String without = java.util.Arrays.stream(commandProviderManifest().split("\n"))
                .filter(line -> !line.trim().startsWith(policy + ":"))
                .reduce((a, b) -> a + "\n" + b).orElseThrow() + "\n";
            assertThatThrownBy(() -> loader.loadFromString(without))
                .as(policy)
                .isInstanceOf(ManifestValidationException.class)
                .hasMessageContaining(policy);
        }
    }

    @Test
    void aCommandConsumerDeclaresTheCommandCapability() {
        String consumer = commandProviderManifest().replace("  consumes: []\nevents:", """
              consumes:
                - commandType: ShipOrder
                  versionRange: ">=1.0.0 <2.0.0"
                  required: true
                  timeoutPolicyRef: event-standard
                  retryPolicyRef: event-standard
                  idempotencyPolicyRef: persistent-inbox
            events:""");
        assertThatThrownBy(() -> loader.loadFromString(consumer))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("Command consumers must declare io.runtime.sdk.capability.CommandCapability");
        String declared = consumer.replace("capabilities:\n  required:\n",
            "capabilities:\n  required:\n    - id: io.runtime.sdk.capability.CommandCapability\n"
                + "      version: \">=3.3.0 <4.0.0\"\n");
        assertThatCode(() -> loader.loadFromString(declared)).doesNotThrowAnyException();
    }

    private String validManifest() {
        return """
            apiVersion: brix.io/v1
            kind: Plugin
            metadata:
              pluginId: sample-plugin
              name: sample-plugin
              version: 3.0.10
              vendor: Brix
              license: Apache-2.0
            runtime:
              compiledAgainst: 3.0.10
              supportedRange: ">=3.0.10 <4.0.0"
            modules:
              - groupId: io.brix
                artifactId: sample-plugin-server
                version: 3.0.10
                moduleKind: plugin-server
            capabilities:
              required:
                - id: AuthCapability
                  version: ">=3.0.10 <4.0.0"
              optional: []
            queries:
              provides: []
              consumes: []
            commands:
              provides: []
              consumes: []
            events:
              publishes: []
              subscribes: []
            """;
    }

    private String reliablePublisherManifest() {
        return validManifest().replace(
            "events:\n  publishes: []\n  subscribes: []\n",
            """
            events:
              publishes:
                - eventType: TenantFirstOwnerAccepted
                  schemaRef: brix://schemas/platform-tenant/tenant-first-owner-accepted/1.0.0
                  version: 1.0.0
                  reliability: CRITICAL
              subscribes: []
            data:
              storageId: platform_tenant
              outbox: platform_tenant_outbox
              inbox: platform_tenant_inbox
            """);
    }

    private String subscriberManifest() {
        return validManifest().replace("capabilities:\n  required:\n",
            "capabilities:\n  required:\n    - id: io.runtime.sdk.capability.PersistenceCapability\n"
                + "      version: \">=3.2.0 <4.0.0\"\n").replace(
            "events:\n  publishes: []\n  subscribes: []\n",
            """
            events:
              publishes: []
              subscribes:
                - subscriptionId: tenant-first-owner-projection
                  eventType: TenantFirstOwnerAccepted
                  schemaRange: ">=1.0.0 <2.0.0"
                  handlerId: tenant-first-owner-projection.v1
                  retryPolicyRef: event-standard
                  idempotencyPolicyRef: persistent-inbox
            data:
              storageId: platform_tenant
              outbox: platform_tenant_outbox
              inbox: platform_tenant_inbox
            """);
    }

    private String queryProviderManifest() {
        return validManifest().replace("queries:\n  provides: []\n", """
            queries:
              provides:
                - queryType: sample-plugin.find-thing
                  request:
                    schemaRef: brix://schemas/sample-plugin/find-thing-request/1.0.0
                  response:
                    schemaRef: brix://schemas/sample-plugin/thing/1.0.0
                  handlerId: find-thing.v1
                  scopePolicyRef: query-caller-tenant
                  accessPolicyRef: declared-consumers
                  timeoutPolicyRef: query-interactive
                  concurrencyPolicyRef: query-standard
            """);
    }

    private String commandProviderManifest() {
        return subscriberManifest().replace("commands:\n  provides: []\n", """
            commands:
              provides:
                - commandType: ReserveStock
                  request:
                    schemaRef: brix://schemas/sample-plugin/reserve-stock/1.0.0
                  handlerId: reserve-stock.v1
                  scopePolicyRef: platform
                  accessPolicyRef: platform
                  timeoutPolicyRef: event-standard
                  concurrencyPolicyRef: partition-serial
                  retryPolicyRef: event-standard
                  idempotencyPolicyRef: persistent-inbox
            """);
    }

    private String templateEndpointManifest() {
        return validManifest().replace(
            "capabilities:\n",
            """
            endpoints:
              provides:
                - endpointId: app-booking.template.status.v1
                  transport: http
                  method: GET
                  path: /api/app-booking/v1/template/status
                  request:
                    schemaRef: brix://schemas/app-booking/template-status-request/1.0.0
                  response:
                    schemaRef: brix://schemas/app-booking/template-status-response/1.0.0
                  handlerId: app-booking.template.status.v1
                  authentication:
                    mode: required
                  authorization:
                    permissions: [app-booking:template:read]
                  tenantContext:
                    mode: optional
                  timeout:
                    policyRef: endpoint-default
                  idempotency:
                    mode: not-applicable
                  accessPolicy: app-booking-template-read
            capabilities:
            """);
    }
}
