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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import io.runtime.manifest.model.PluginManifest;
import io.runtime.manifest.validation.ManifestValidationException;
import io.runtime.manifest.validation.PluginManifestSchema;

/**
 * Standard-link queue item 17 (D152, D153): the loader takes only what the plugin manifest's JSON Schema allows, in
 * every host. Each vector below changes one thing in a manifest that has one declaration of each kind, and the loader
 * must refuse it and name the field. A query or command declaration takes only its own fields (root
 * BRX-RS-MANIFEST-025); earlier names are gone; a plugin declares no {@code internalContracts.requires} and leaves out
 * no section (MANIFEST-001). The manifests in this repository are the valid vectors.
 */
class PluginManifestVectorsTest {

    private static final YAMLMapper YAML = YAMLMapper.builder().build();
    private static final Pattern PATH_STEP = Pattern.compile("([A-Za-z]+)(?:\\[(\\d+)])?");

    /** One declaration of each kind the schema has. Each vector changes exactly one thing in it. */
    private static final String BASE = """
        apiVersion: brix.io/v1
        kind: Plugin
        metadata:
          pluginId: sample-plugin
          name: sample-plugin
          version: 1.0.0
          vendor: Brix
          license: Apache-2.0
        runtime:
          compiledAgainst: 3.2.0
          supportedRange: ">=3.2.0 <4.0.0"
        modules:
          - groupId: io.brix
            artifactId: sample-plugin-server
            version: 1.0.0
            moduleKind: plugin-server
        endpoints:
          provides:
            - endpointId: sample-plugin.status.v1
              transport: http
              method: GET
              path: /api/sample-plugin/v1/status
              request:
                schemaRef: brix://schemas/sample-plugin/status-request/1.0.0
              response:
                schemaRef: brix://schemas/sample-plugin/status-response/1.0.0
              handlerId: sample-plugin.status.v1
              authentication:
                mode: required
              authorization:
                permissions: [sample-plugin:read]
              tenantContext:
                mode: optional
              timeout:
                policyRef: endpoint-default
              idempotency:
                mode: not-applicable
        capabilities:
          required:
            - id: io.runtime.sdk.capability.PersistenceCapability
              version: ">=3.2.0 <4.0.0"
            - id: io.runtime.sdk.capability.QueryCapability
              version: ">=3.3.0 <4.0.0"
            - id: io.runtime.sdk.capability.CommandCapability
              version: ">=3.3.0 <4.0.0"
          optional: []
        queries:
          provides:
            - queryType: sample-plugin.find-thing
              request:
                schemaRef: brix://schemas/sample-plugin/find-thing-request/1.0.0
              response:
                schemaRef: brix://schemas/sample-plugin/thing/1.0.0
              handlerId: sample-plugin.find-thing.v1
              scopePolicyRef: query-caller-tenant
              accessPolicyRef: declared-consumers
              timeoutPolicyRef: query-interactive
              concurrencyPolicyRef: query-standard
          consumes:
            - queryType: other-plugin.find-other
              versionRange: ">=1.0.0 <2.0.0"
              required: false
              timeoutPolicyRef: query-interactive
              retryPolicyRef: query-no-retry
        commands:
          provides:
            - commandType: sample-plugin.reserve-stock
              request:
                schemaRef: brix://schemas/sample-plugin/reserve-stock/1.0.0
              handlerId: sample-plugin.reserve-stock.v1
              scopePolicyRef: platform
              accessPolicyRef: platform
              timeoutPolicyRef: command-standard
              concurrencyPolicyRef: partition-serial
              retryPolicyRef: command-transient-retry
              idempotencyPolicyRef: command-key
          consumes:
            - commandType: other-plugin.ship-order
              versionRange: ">=1.0.0 <2.0.0"
              required: true
        events:
          publishes:
            - eventType: SampleThingChanged
              schemaRef: brix://schemas/sample-plugin/thing-changed/1.0.0
              version: 1.0.0
              reliability: STANDARD
          subscribes:
            - subscriptionId: other-thing-projection
              eventType: OtherThingChanged
              schemaRange: ">=1.0.0 <2.0.0"
              handlerId: sample-plugin.other-thing-projection.v1
              scopePolicyRef: other-thing-projection-scope
              publisherPolicyRef: other-plugin-owner
              timeoutPolicyRef: event-standard
              concurrencyPolicyRef: partition-serial
              retryPolicyRef: event-standard
              idempotencyPolicyRef: persistent-inbox
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
        internalContracts:
          provides:
            - contractId: sample-plugin.administration
              contractType: io.example.SampleAdministration
              contractVersion: 1.0.0
              providerId: sample-plugin.internal
              owner: sample-plugin
        externalServices:
          - id: keys
            baseUrlRef: keys
            timeoutPolicyRef: external-default
            retryPolicyRef: external-no-retry
            concurrencyPolicyRef: external-standard
        data:
          storageId: sample_plugin
          outbox: sample_plugin_outbox
          inbox: sample_plugin_inbox
          custodialWrites:
            - table: sys_platform_audit_log
              custodian: runtime.tenant-policy-audit
              operations: [INSERT]
              columns: [action, trace_id, writer]
              actions: [LOGIN_TENANT_DISCOVERY]
              recordSchemaRef: brix://schemas/platform-audit/policy-use-record/1.0.0
        permissions:
          - id: sample-plugin:read
            name: Read things
            grants:
              - role: MEMBER
                scope: TENANT
        config:
          - key: sample-plugin.page-size
            type: integer
            default: 20
            sensitivity: internal
        ui:
          web:
            enabled: false
        """;

    /**
     * Where each class of the manifest model sits in {@link #BASE}, and the part of the schema that describes it. The
     * model has a field for every property the schema allows; any further field it has is one the loader must refuse.
     */
    private static final List<Section> SECTIONS = List.of(
        new Section("", PluginManifest.class, ""),
        new Section("metadata", PluginManifest.Metadata.class, "/definitions/metadata"),
        new Section("runtime", PluginManifest.Runtime.class, "/definitions/runtime"),
        new Section("modules[0]", PluginManifest.Module.class, "/definitions/module"),
        new Section("endpoints", PluginManifest.EndpointSection.class, "/properties/endpoints"),
        new Section("endpoints.provides[0]", PluginManifest.Route.class, "/definitions/endpoint"),
        new Section("capabilities", PluginManifest.CapabilitySection.class, "/properties/capabilities"),
        new Section("capabilities.required[0]", PluginManifest.CapabilityRef.class, "/definitions/capabilityRef"),
        new Section("queries", PluginManifest.ContractSection.class, "/properties/queries"),
        new Section("queries.provides[0]", PluginManifest.ContractRef.class, "/definitions/queryProvider"),
        new Section("queries.consumes[0]", PluginManifest.ContractRef.class, "/definitions/queryConsumer"),
        new Section("commands", PluginManifest.ContractSection.class, "/properties/commands"),
        new Section("commands.provides[0]", PluginManifest.ContractRef.class, "/definitions/commandProvider"),
        new Section("commands.consumes[0]", PluginManifest.ContractRef.class, "/definitions/commandConsumer"),
        new Section("events", PluginManifest.EventSection.class, "/properties/events"),
        new Section("events.publishes[0]", PluginManifest.EventPublish.class, "/definitions/eventPublish"),
        new Section("events.subscribes[0]", PluginManifest.EventSubscribe.class, "/definitions/eventSubscribe"),
        new Section("tasks", PluginManifest.TaskSection.class, "/properties/tasks"),
        new Section("tasks.provides[0]", PluginManifest.Task.class, "/definitions/task"),
        new Section("internalContracts", PluginManifest.InternalContractSection.class, "/properties/internalContracts"),
        new Section("internalContracts.provides[0]", PluginManifest.ProvidedInternalContract.class,
            "/definitions/providedInternalContract"),
        new Section("externalServices[0]", PluginManifest.ExternalService.class, "/definitions/externalService"),
        new Section("data", PluginManifest.DataSection.class, "/definitions/dataSection"),
        new Section("data.custodialWrites[0]", PluginManifest.CustodialWrite.class, "/definitions/custodialWrite"),
        new Section("permissions[0]", PluginManifest.Permission.class, "/definitions/permission"),
        new Section("permissions[0].grants[0]", PluginManifest.Grant.class, "/definitions/permissionGrant"),
        new Section("config[0]", PluginManifest.ConfigEntry.class, "/definitions/configEntry"),
        new Section("ui", PluginManifest.UiSection.class, "/definitions/uiSection"),
        new Section("ui.web", PluginManifest.UiPlatform.class, "/definitions/uiPlatform"));

    private final PluginManifestLoader loader = new PluginManifestLoader();

    @Test
    void theBaseLoads() {
        assertThatCode(() -> loader.loadFromString(BASE)).doesNotThrowAnyException();
    }

    /** The examples in queue item 17's row: before D152 each loaded and took no effect. */
    @ParameterizedTest(name = "{0}[0].{1}")
    @CsvSource({
        "commands.consumes, scopePolicyRef, platform",
        "commands.consumes, handlerId, sample-plugin.ship-order.v1",
        "commands.provides, versionRange, '>=1.0.0 <2.0.0'",
        "queries.provides, retryPolicyRef, query-no-retry",
        "queries.provides, idempotencyPolicyRef, command-key"})
    void aFieldOfAnotherEntryDoesNotLoad(String entry, String field, String value) {
        assertRefused(with(entry + "[0]", field, JsonNodeFactory.instance.stringNode(value)), entry + "[0]." + field);
    }

    /**
     * Every field the model has that a declaration's part of the schema leaves out, on that declaration: another
     * entry's field, or an earlier name such as an endpoint's {@code handler} or an event's {@code schema}.
     */
    @TestFactory
    Stream<DynamicTest> noDeclarationTakesAFieldItsSchemaLeavesOut() {
        JsonNode schema = schema();
        List<DynamicTest> tests = new ArrayList<>();
        for (Section section : SECTIONS) {
            Set<String> allowed = new LinkedHashSet<>();
            schema.at(section.schemaPointer() + "/properties").propertyNames().forEach(allowed::add);
            assertThat(allowed).as("the schema describes " + section.path()).isNotEmpty();
            for (Field field : section.model().getDeclaredFields()) {
                String name = jsonName(field);
                if (Modifier.isStatic(field.getModifiers()) || allowed.contains(name)) {
                    continue;
                }
                String path = section.path().isEmpty() ? name : section.path() + "." + name;
                tests.add(DynamicTest.dynamicTest(path, () -> assertRefused(with(section.path(), name, value(field)),
                    path)));
            }
        }
        // The fields queue item 17 found, among them: a run that generates none of them has stopped looking.
        assertThat(tests).extracting(DynamicTest::getDisplayName).contains("commands.consumes[0].handlerId",
            "commands.provides[0].versionRange", "queries.provides[0].idempotencyPolicyRef", "routes",
            "endpoints.provides[0].handler", "events.publishes[0].schema", "internalContracts.requires");
        return tests.stream();
    }

    /** Every section the schema requires is there, even when it declares nothing (root BRX-RS-MANIFEST-001). */
    @ParameterizedTest
    @ValueSource(strings = {"apiVersion", "kind", "metadata", "runtime", "modules", "endpoints", "capabilities",
        "queries", "commands", "events", "tasks"})
    void noSectionTheSchemaRequiresIsLeftOut(String section) {
        ObjectNode manifest = base();
        manifest.remove(section);
        assertRefused(yaml(manifest), section);
    }

    /** A plugin manifest declares no {@code internalContracts.requires} (root BRX-RS-MANIFEST-001). */
    @Test
    void aPluginRequiresNoInternalContract() {
        ArrayNode requires = JsonNodeFactory.instance.arrayNode();
        requires.addObject()
            .put("contractId", "platform-tenant.administration")
            .put("contractType", "io.example.TenantAdministration")
            .put("versionRange", ">=1.0.0 <2.0.0")
            .put("required", true)
            .put("privilegeAllowlistRef", "tenant-administration");
        assertRefused(with("internalContracts", "requires", requires), "internalContracts.requires");
    }

    /** The manifests in this repository are the valid vectors (root BRX-RS-CAP-010). */
    @ParameterizedTest
    @ValueSource(strings = {
        "../../samples/sample-orders-server/src/main/resources/META-INF/brix/plugin-manifest.yaml",
        "../../samples/sample-shipping-server/src/main/resources/META-INF/brix/plugin-manifest.yaml",
        "../../platform-commons/packages/server/platform-identity/src/main/resources/META-INF/brix/plugin-manifest.yaml",
        "../../platform-commons/packages/server/platform-tenant/src/main/resources/META-INF/brix/plugin-manifest.yaml",
        "../../platform-commons/packages/server/platform-auth/src/main/resources/META-INF/brix/plugin-manifest.yaml",
        "../../platform-commons/packages/server/platform-audit/platform-audit-server/src/main/resources/META-INF/brix/"
            + "plugin-manifest.yaml",
        "../../infra-adapters/packages/server/infra-adapter-persistence-jpa/src/test/resources/META-INF/brix/"
            + "plugin-manifest.yaml",
        "../runtime-orchestrator/src/test/resources/META-INF/brix/plugin-manifest.yaml"})
    void theRepositorysManifestsLoad(String file) throws IOException {
        Path path = Path.of(file);
        assumeTrue(Files.exists(path), "the manifests sit next to the runtime SDK in the repository");
        String manifest = Files.readString(path, StandardCharsets.UTF_8);
        assertThat(PluginManifestSchema.check(YAML.readTree(manifest)).getErrors()).isEmpty();
        assertThatCode(() -> loader.loadFromString(manifest)).doesNotThrowAnyException();
    }

    private void assertRefused(String manifest, String field) {
        assertThatThrownBy(() -> loader.loadFromString(manifest))
            .as(field)
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("[" + field + "]")
            .hasMessageContaining("plugin-manifest.schema.json");
    }

    /** {@link #BASE} with one more field at the given path. */
    private static String with(String path, String field, JsonNode value) {
        ObjectNode manifest = base();
        ((ObjectNode) at(manifest, path)).set(field, value);
        return yaml(manifest);
    }

    private static JsonNode at(ObjectNode manifest, String path) {
        JsonNode node = manifest;
        if (path.isEmpty()) {
            return node;
        }
        for (String step : path.split("\\.")) {
            Matcher matcher = PATH_STEP.matcher(step);
            assertThat(matcher.matches()).as(step).isTrue();
            node = node.get(matcher.group(1));
            if (matcher.group(2) != null) {
                node = node.get(Integer.parseInt(matcher.group(2)));
            }
            assertThat(node).as(path + " in the base manifest").isNotNull();
        }
        return node;
    }

    /** A value of the type the model reads, so that the schema's objection is the only one. */
    private static JsonNode value(Field field) {
        JsonNodeFactory nodes = JsonNodeFactory.instance;
        Class<?> type = field.getType();
        if (type == Boolean.class || type == boolean.class) {
            return nodes.booleanNode(true);
        }
        if (Map.class.isAssignableFrom(type)) {
            return nodes.objectNode().put("schemaRef", "brix://schemas/sample-plugin/thing/1.0.0");
        }
        if (List.class.isAssignableFrom(type)) {
            return nodes.arrayNode();
        }
        return nodes.stringNode("sample-plugin.thing");
    }

    private static String jsonName(Field field) {
        JsonProperty property = field.getAnnotation(JsonProperty.class);
        return property != null && !property.value().isEmpty() ? property.value() : field.getName();
    }

    private static ObjectNode base() {
        return (ObjectNode) YAML.readTree(BASE);
    }

    private static String yaml(JsonNode manifest) {
        return YAML.writeValueAsString(manifest);
    }

    private static JsonNode schema() {
        try (InputStream in = PluginManifestVectorsTest.class.getClassLoader()
                .getResourceAsStream(PluginManifestSchema.RESOURCE)) {
            assertThat(in).as(PluginManifestSchema.RESOURCE).isNotNull();
            return JsonMapper.builder().build().readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Section(String path, Class<?> model, String schemaPointer) {
    }
}
