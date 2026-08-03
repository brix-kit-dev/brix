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
package io.runtime.orchestrator.manifest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PluginManifestSchemaTest {

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
        ObjectMapper objectMapper = new ObjectMapper();
        PluginManifestValidator validator = new PluginManifestValidator(objectMapper);

        assertThrows(PluginManifestValidationException.class,
            () -> validator.validate("missing reliability", objectMapper.readTree(manifestWithoutReliability())));
    }

    @Test
    void bundledSchemaAcceptsPublishedEventReliability() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        PluginManifestValidator validator = new PluginManifestValidator(objectMapper);

        assertDoesNotThrow(() -> validator.validate("valid reliability", objectMapper.readTree(manifestWithReliability())));
    }

    @Test
    void bundledSchemaAcceptsEndpointProvides() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        PluginManifestValidator validator = new PluginManifestValidator(objectMapper);

        assertDoesNotThrow(() -> validator.validate(
            "valid endpoint provides",
            objectMapper.readTree(manifestWithEndpointProvides())));
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
