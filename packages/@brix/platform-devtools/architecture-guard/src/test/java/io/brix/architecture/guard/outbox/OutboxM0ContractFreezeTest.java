package io.brix.architecture.guard.outbox;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the M0 reliable-message freeze artifact from accidental drift before
 * the runtime contract implementation milestones are accepted.
 */
class OutboxM0ContractFreezeTest {

    private static final List<String> REQUIRED_TOKENS = List.of(
            "\"schemaVersion\": \"1.0.0\"",
            "\"runtime-shell-3.0.10\"",
            "\"BRX-RS-MESSAGE-017\"",
            "\"BRX-RS-MESSAGE-021\"",
            "\"BRX-RS-MESSAGE-029\"",
            "\"BRX-RS-DATA-008\"",
            "\"CRITICAL\"",
            "\"STANDARD\"",
            "\"BEST_EFFORT\"",
            "\"EVENT\"",
            "\"COMMAND\"",
            "\"eventId\"",
            "\"eventType\"",
            "\"schemaVersion\"",
            "\"occurredAt\"",
            "\"producerPluginId\"",
            "\"tenantId\"",
            "\"traceparent\"",
            "\"partitionKey\"",
            "\"message_id\"",
            "\"message_kind\"",
            "\"schema_version\"",
            "\"producer_plugin_id\"",
            "\"available_at\"",
            "\"attempt_count\"",
            "\"claim_owner\"",
            "\"claim_until\"",
            "\"last_error_code\"",
            "\"handler_id\"",
            "\"processed_at\"",
            "\"PENDING\"",
            "\"IN_FLIGHT\"",
            "\"PUBLISHED\"",
            "\"PARKED\"",
            "\"TenantFirstOwnerAccepted\"",
            "\"platform-tenant\"",
            "\"platform_tenant\"",
            "\"kafka\"",
            "\"cdc\"");

    @Test
    @DisplayName("M0 contract freeze file contains the required reliable-message anchors")
    void m0ContractFreezeContainsRequiredAnchors() throws IOException {
        String contract = Files.readString(findContractFile());

        for (String token : REQUIRED_TOKENS) {
            assertTrue(contract.contains(token), "Missing M0 contract token: " + token);
        }
        assertTrue(contract.contains("\"bestEffortAtLeastOnce\": false"));
        assertTrue(contract.contains("Kafka is the first L2C transport mapping only"));
    }

    private static Path findContractFile() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path direct = current.resolve("../schemas/outbox-message-contract.v1.json").normalize();
            if (Files.isRegularFile(direct)) {
                return direct;
            }

            Path repoRoot = current.resolve("packages/@brix/platform-devtools/schemas/outbox-message-contract.v1.json");
            if (Files.isRegularFile(repoRoot)) {
                return repoRoot;
            }

            current = current.getParent();
        }

        throw new IllegalStateException("Cannot locate outbox-message-contract.v1.json");
    }
}
