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
        String consumer = commandSenderManifest().replace(
            "    - id: io.runtime.sdk.capability.CommandCapability\n      version: \">=3.3.0 <4.0.0\"\n", "");
        assertThatThrownBy(() -> loader.loadFromString(consumer))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("Command consumers must declare io.runtime.sdk.capability.CommandCapability");
        assertThatCode(() -> loader.loadFromString(commandSenderManifest())).doesNotThrowAnyException();
    }

    @Test
    void aCommandSenderDeclaresNoPolicies() {
        // Root BRX-RS-MANIFEST-025, MANIFEST-026 (standard-link plan D102): the command is delivered under its
        // provider's policies, so a policy its sender wrote would take no effect; even an empty one is refused.
        for (String policy : java.util.List.of("timeoutPolicyRef: event-standard", "retryPolicyRef: event-standard",
                "idempotencyPolicyRef: persistent-inbox", "timeoutPolicyRef: \"\"")) {
            String field = policy.substring(0, policy.indexOf(':'));
            String kind = field.substring(0, field.length() - "PolicyRef".length());
            String withPolicy = commandSenderManifest().replace("      required: true\n",
                "      required: true\n      " + policy + "\n");
            assertThatThrownBy(() -> loader.loadFromString(withPolicy))
                .as(policy)
                .isInstanceOf(ManifestValidationException.class)
                .hasMessageContaining("commands.consumes[0]." + field)
                .hasMessageContaining("A command sender declares no " + kind + " policy");
        }
    }

    @Test
    void aTaskNamesEveryPolicy() {
        // Root BRX-RS-MANIFEST-025 (standard-link plan L6.2): the nine fields of a task.
        assertThatCode(() -> loader.loadFromString(taskManifest(""))).doesNotThrowAnyException();
        for (String field : java.util.List.of("taskId", "handlerId", "scopePolicyRef", "executionIdentityPolicyRef",
                "triggerPolicyRef", "timeoutPolicyRef", "concurrencyPolicyRef", "retryPolicyRef", "idempotencyPolicyRef")) {
            String without = java.util.Arrays.stream(taskManifest("").split("\n"))
                .filter(line -> !line.trim().startsWith(field + ":") && !line.trim().startsWith("- " + field + ":"))
                .reduce((a, b) -> a + "\n" + b).orElseThrow() + "\n";
            assertThatThrownBy(() -> loader.loadFromString(without.replace("  provides:\n      handlerId",
                    "  provides:\n    - handlerId")))
                .as(field)
                .isInstanceOf(ManifestValidationException.class)
                .hasMessageContaining(field);
        }
    }

    @Test
    void aTaskRunsInTheUnitOfWorkItDeclaresAndThatNeedsPersistence() {
        // Root BRX-RS-DATA-028: read-write unless the task declares read-only or none.
        assertThatThrownBy(() -> loader.loadFromString(taskManifest("")
                .replace("    - id: io.runtime.sdk.capability.PersistenceCapability\n      version: \">=3.2.0 <4.0.0\"\n", "")))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("transaction: none");
        assertThatCode(() -> loader.loadFromString(taskManifest("      transaction: none\n")
                .replace("    - id: io.runtime.sdk.capability.PersistenceCapability\n      version: \">=3.2.0 <4.0.0\"\n", "")))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> loader.loadFromString(taskManifest("      transaction: serializable\n")))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("required, read-only or none");
    }

    @Test
    void anExternalServiceNamesItsDirectoryEntryAndItsPolicies() {
        // Root BRX-RS-ERROR-014; standard-link plan L8.2.
        assertThatCode(() -> loader.loadFromString(externalManifest("google-oauth-jwks", "google-oauth-jwks")))
            .doesNotThrowAnyException();
        for (String field : java.util.List.of("timeoutPolicyRef", "retryPolicyRef", "concurrencyPolicyRef")) {
            String without = java.util.Arrays.stream(externalManifest("keys", "keys").split("\n"))
                .filter(line -> !line.trim().startsWith(field + ":"))
                .reduce((a, b) -> a + "\n" + b).orElseThrow() + "\n";
            assertThatThrownBy(() -> loader.loadFromString(without))
                .as(field)
                .isInstanceOf(ManifestValidationException.class)
                .hasMessageContaining(field);
        }
        assertThatThrownBy(() -> loader.loadFromString(externalManifest("Keys!", "keys")))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("externalServices[0].id");
        assertThatThrownBy(() -> loader.loadFromString(externalManifest("keys", "https://keys.example.test")))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("baseUrlRef");
    }

    private String externalManifest(String id, String baseUrlRef) {
        return validManifest() + """
            externalServices:
              - id: ID
                baseUrlRef: REF
                timeoutPolicyRef: external-default
                retryPolicyRef: external-no-retry
                concurrencyPolicyRef: external-standard
            """.replace("ID", id).replace("REF", baseUrlRef);
    }

    private String taskManifest(String extra) {
        return validManifest().replace("capabilities:\n  required:\n",
            "capabilities:\n  required:\n    - id: io.runtime.sdk.capability.PersistenceCapability\n"
                + "      version: \">=3.2.0 <4.0.0\"\n")
            + """
            tasks:
              provides:
                - taskId: sample-plugin.heartbeat
                  handlerId: sample-plugin.heartbeat.v1
                  scopePolicyRef: task-platform
                  executionIdentityPolicyRef: plugin-service
                  triggerPolicyRef: every:PT1H
                  timeoutPolicyRef: task-standard
                  concurrencyPolicyRef: task-no-overlap
                  retryPolicyRef: task-no-retry
                  idempotencyPolicyRef: task-trigger
            data:
              storageId: sample_plugin
            """.replace("data:\n", extra + "data:\n");
    }

    @Test
    void anEndpointDeclaresTheStatusItsSuccessAnswersWith() {
        // Standard-link plan L7.3: 200 unless the endpoint names 201, 202 or 204.
        assertThatCode(() -> loader.loadFromString(templateEndpointManifest().replace(
                "template-status-response/1.0.0\n",
                "template-status-response/1.0.0\n        status: 202\n")))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> loader.loadFromString(templateEndpointManifest().replace(
                "template-status-response/1.0.0\n",
                "template-status-response/1.0.0\n        status: 418\n")))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("200, 201, 202 or 204");
    }

    @Test
    void anEndpointCannotDeclareOptionalIdempotency() {
        // Root BRX-RS-MANIFEST-018; idempotency design D72: the runtime provides required only.
        assertThatThrownBy(() -> loader.loadFromString(replayedEndpointManifest()
                .replace("idempotency:\n        mode: required", "idempotency:\n        mode: optional")))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("optional is not provided");
    }

    @Test
    void anEndpointWhoseResultsAreReplayedIsATypedWriteOfAVerifiedCallerAndHasAnOwner() {
        // Root BRX-RS-MANIFEST-018; standard-link plan point 10a, idempotency design D74.
        assertThatCode(() -> loader.loadFromString(replayedEndpointManifest())).doesNotThrowAnyException();
        assertThatThrownBy(() -> loader.loadFromString(replayedEndpointManifest().replace("method: POST", "method: GET")))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("Only POST, PUT, PATCH and DELETE");
        assertThatThrownBy(() -> loader.loadFromString(replayedEndpointManifest().replace(
                "      response:\n        schemaRef: brix://schemas/app-booking/template-status-response/1.0.0\n", "")))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("request and response schemas");
        assertThatThrownBy(() -> loader.loadFromString(replayedEndpointManifest().replace(
                "authentication:\n        mode: required\n", "authentication:\n        mode: anonymous\n")))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("authentication.mode required");
        assertThatThrownBy(() -> loader.loadFromString(replayedEndpointManifest().replace(
                "data:\n  storageId: app_booking\n", "")))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("data.storageId");
        assertThatThrownBy(() -> loader.loadFromString(replayedEndpointManifest().replace(
                "    - id: io.runtime.sdk.capability.PersistenceCapability\n      version: \">=3.2.0 <4.0.0\"\n", "")))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("must require PersistenceCapability");
    }

    @Test
    void anEndpointWithoutABodyToKeepNeedsNoResponseSchema() {
        // A 204 has no answer but its status, which the runtime replays all the same.
        assertThatCode(() -> loader.loadFromString(replayedEndpointManifest().replace(
                "      response:\n        schemaRef: brix://schemas/app-booking/template-status-response/1.0.0\n",
                "      response:\n        status: 204\n")))
            .doesNotThrowAnyException();
    }

    @Test
    void anOwnerOfReplayableWritesHasTheirTable() {
        // Idempotency design D79: the owner's manifest decides, whoever calls the contract.
        String owner = validManifest() + REPLAYABLE_WRITE;
        assertThatThrownBy(() -> loader.loadFromString(owner))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("An owner of replayable writes needs data.storageId")
            .hasMessageContaining("must require PersistenceCapability");
    }

    @Test
    void aManifestSaysWhetherItsOwnerKeepsEndpointResults() {
        String notReplayed = replayedEndpointManifest().replace("idempotency:\n        mode: required",
            "idempotency:\n        mode: not-applicable");
        assertThat(loader.loadFromString(replayedEndpointManifest()).keepsEndpointResults()).isTrue();
        assertThat(loader.loadFromString(notReplayed).keepsEndpointResults()).isFalse();
        assertThat(loader.loadFromString(notReplayed + REPLAYABLE_WRITE).keepsEndpointResults()).isTrue();
    }

    private static final String REPLAYABLE_WRITE = """
        internalContracts:
          provides:
            - contractId: app-booking.administration
              contractType: io.example.BookingAdministration
              contractVersion: 1.0.0
              providerId: app-booking.internal
              owner: app-booking
              replayableWrites: [cancel]
        """;

    private String replayedEndpointManifest() {
        return templateEndpointManifest()
            .replace("method: GET", "method: POST")
            .replace("mode: not-applicable", "mode: required")
            .replace("capabilities:\n  required:\n", "capabilities:\n  required:\n"
                + "    - id: io.runtime.sdk.capability.PersistenceCapability\n      version: \">=3.2.0 <4.0.0\"\n")
            + "data:\n  storageId: app_booking\n";
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

    /** The command provider's manifest, also sending ShipOrder: a sender names the type, its range and required. */
    private String commandSenderManifest() {
        return commandProviderManifest()
            .replace("capabilities:\n  required:\n",
                "capabilities:\n  required:\n    - id: io.runtime.sdk.capability.CommandCapability\n"
                    + "      version: \">=3.3.0 <4.0.0\"\n")
            .replace("  consumes: []\nevents:", """
                  consumes:
                    - commandType: ShipOrder
                      versionRange: ">=1.0.0 <2.0.0"
                      required: true
                events:""");
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
                  timeoutPolicyRef: command-standard
                  concurrencyPolicyRef: partition-serial
                  retryPolicyRef: command-transient-retry
                  idempotencyPolicyRef: command-key
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
