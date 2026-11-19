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
package io.runtime.manifest.validation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

/** The plugin manifest's JSON Schema, which the loader checks every manifest against (standard-link plan D152). */
class PluginManifestSchemaTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void bundledSchemaMatchesDevtoolsAuthoringSchema() throws IOException {
        byte[] bundled = Files.readAllBytes(Path.of(
            "src/main/resources/META-INF/schemas/plugin-manifest.schema.json"));
        byte[] devtools = Files.readAllBytes(Path.of(
            "../../platform-devtools/schemas/plugin-manifest.schema.json"));

        assertArrayEquals(devtools, bundled);
    }

    @Test
    void bundledSchemaRequiresPublishedEventReliability() throws IOException {
        assertFalse(check(manifestWithoutReliability()).isValid());
    }

    @Test
    void bundledSchemaAcceptsPublishedEventReliability() throws IOException {
        assertValid(manifestWithReliability());
    }

    @Test
    void bundledSchemaAcceptsEndpointProvides() throws IOException {
        assertValid(manifestWithEndpointProvides());
    }

    @Test
    void bundledSchemaAcceptsACustodialWriteRegistration() throws IOException {
        assertValid(manifestWithCustodialWrite("[\"INSERT\"]", "runtime.tenant-policy-audit"));
    }

    @Test
    void bundledSchemaRefusesCustodialWritesBeyondInsertOrTheCustodianDirectory() throws IOException {
        // Root BRX-RS-MANIFEST-031: only INSERT, and only a Runtime Custodian of appendix A.
        assertFalse(check(manifestWithCustodialWrite("[\"INSERT\", \"UPDATE\"]", "runtime.tenant-policy-audit")).isValid());
        assertFalse(check(manifestWithCustodialWrite("[\"INSERT\"]", "runtime.other")).isValid());
        String withoutRecordSchema = manifestWithCustodialWrite("[\"INSERT\"]", "runtime.tenant-policy-audit")
            .replace("\"actions\": [\"LOGIN_TENANT_DISCOVERY\"],", "\"actions\": [\"LOGIN_TENANT_DISCOVERY\"]")
            .replace("\"recordSchemaRef\": \"brix://schemas/platform-audit/policy-use-record/1.0.0\"", "");
        assertFalse(check(withoutRecordSchema).isValid());
    }

    @Test
    void bundledSchemaTakesACommandSenderWithoutPolicies() throws IOException {
        // Standard-link plan D102: a sender names the command, its range and whether it requires it, and no policy.
        assertValid(manifestWithCommandSender(""));
        for (String policy : List.of("timeoutPolicyRef", "retryPolicyRef", "idempotencyPolicyRef")) {
            ValidationResult refused = check(manifestWithCommandSender(", \"" + policy + "\": \"event-standard\""));
            assertTrue(refused.getErrors().stream()
                    .anyMatch(error -> error.getField().equals("commands.consumes[0]." + policy)),
                refused.getErrorMessage());
        }
    }

    private ValidationResult check(String json) throws IOException {
        return PluginManifestSchema.check(objectMapper.readTree(json));
    }

    private void assertValid(String json) throws IOException {
        ValidationResult result = check(json);
        assertTrue(result.isValid(), result.getErrorMessage());
    }

    private String manifestWithCommandSender(String policy) {
        String sender = """
            "commands": {"provides": [], "consumes": [
                {"commandType": "sample-orders.cancel-order", "versionRange": ">=1.0.0 <2.0.0", "required": true%s}
              ]},""".formatted(policy);
        return manifestWithReliability().replace("\"commands\": {\"provides\": [], \"consumes\": []},", sender);
    }

    private String manifestWithCustodialWrite(String operations, String custodian) {
        return manifestWithReliability().replace("""
                "inbox": "platform_tenant_inbox"
            """, """
                "inbox": "platform_tenant_inbox",
                "custodialWrites": [
                  {
                    "table": "sys_platform_audit_log",
                    "custodian": "%s",
                    "operations": %s,
                    "columns": ["id", "operator_identity_id", "action", "trace_id", "writer"],
                    "actions": ["LOGIN_TENANT_DISCOVERY"],
                    "recordSchemaRef": "brix://schemas/platform-audit/policy-use-record/1.0.0"
                  }
                ]
            """.formatted(custodian, operations));
    }

    private String manifestWithReliability() {
        return """
            {
              "apiVersion": "brix.io/v1",
              "kind": "Plugin",
              "metadata": {
                "pluginId": "platform-tenant",
                "name": "platform-tenant",
                "version": "3.2.0",
                "vendor": "Brix",
                "license": "Apache-2.0"
              },
              "runtime": {
                "compiledAgainst": "3.2.0",
                "supportedRange": ">=3.0.10 <4.0.0"
              },
              "modules": [
                {
                  "groupId": "io.brix.platform",
                  "artifactId": "platform-tenant",
                  "version": "3.2.0",
                  "moduleKind": "plugin-server"
                }
              ],
              "capabilities": {"required": [], "optional": []},
              "queries": {"provides": [], "consumes": []},
              "commands": {"provides": [], "consumes": []},
              "events": {
                "publishes": [
                  {
                    "eventType": "TenantFirstOwnerAccepted",
                    "schemaRef": "brix://schemas/platform-tenant/tenant-first-owner-accepted/1.0.0",
                    "version": "1.0.0",
                    "reliability": "CRITICAL"
                  }
                ],
                "subscribes": []
              },
              "endpoints": {"provides": []},
              "tasks": {"provides": []},
              "data": {
                "storageId": "platform_tenant",
                "outbox": "platform_tenant_outbox",
                "inbox": "platform_tenant_inbox"
              }
            }
            """;
    }

    private String manifestWithoutReliability() {
        return manifestWithReliability().replace("""
                    "reliability": "CRITICAL"
            """, """
                    "schemaRef": "brix://schemas/platform-tenant/tenant-first-owner-accepted/1.0.0"
            """);
    }

    private String manifestWithEndpointProvides() {
        return """
            {
              "apiVersion": "brix.io/v1",
              "kind": "Plugin",
              "metadata": {
                "pluginId": "app-booking",
                "name": "app-booking",
                "version": "3.2.0-SNAPSHOT",
                "vendor": "Brix Enterprise",
                "license": "Commercial"
              },
              "runtime": {
                "compiledAgainst": "3.2.0",
                "supportedRange": ">=3.0.10 <4.0.0"
              },
              "modules": [
                {
                  "groupId": "io.brix.enterprise.solutions",
                  "artifactId": "booking-server",
                  "version": "3.2.0-SNAPSHOT",
                  "moduleKind": "plugin-server"
                }
              ],
              "endpoints": {
                "provides": [
                  {
                    "endpointId": "app-booking.template.status.v1",
                    "transport": "http",
                    "method": "GET",
                    "path": "/api/app-booking/v1/template/status",
                    "request": {"schemaRef": "brix://schemas/app-booking/template-status-request/1.0.0"},
                    "response": {"schemaRef": "brix://schemas/app-booking/template-status-response/1.0.0"},
                    "handlerId": "app-booking.template.status.v1",
                    "authentication": {"mode": "required"},
                    "authorization": {"permissions": ["app-booking:template:read"]},
                    "tenantContext": {"mode": "optional"},
                    "timeout": {"policyRef": "endpoint-default"},
                    "idempotency": {"mode": "not-applicable"}
                  }
                ]
              },
              "capabilities": {"required": [], "optional": []},
              "queries": {"provides": [], "consumes": []},
              "commands": {"provides": [], "consumes": []},
              "events": {"publishes": [], "subscribes": []},
              "tasks": {"provides": []}
            }
            """;
    }
}
