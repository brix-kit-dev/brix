/*
 * Copyright 2026 Brix Platform Authors
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
package io.brix.devtools.governance;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ArchitectureGovernanceRegistryCompletenessTest {

    private static final Path REGISTRY = Path.of(
        "src/main/resources/io/brix/devtools/governance/requirements.registry.yaml");
    private static final Path SCHEMA = Path.of(
        "src/main/resources/io/brix/devtools/governance/requirements.registry.schema.yaml");
    private static final List<String> REQUIRED_REQUIREMENT_FIELDS = List.of(
        "id", "owner", "tool", "test", "status", "evidence");
    private static final List<String> REQUIRED_RULE_FIELDS = List.of(
        "id", "kind", "wired", "scope", "targetCount", "test", "status", "evidence");
    private static final Set<String> FORBIDDEN_PHASE0_STATUSES = Set.of(
        "pass", "passing", "enforced-passing", "accepted", "ga");

    @Test
    void registryCoversActiveRequirementFamiliesAndKeepsGapsBlocking() throws IOException {
        String registry = Files.readString(REGISTRY);

        validateSection(registry, "clauseGroups", REQUIRED_REQUIREMENT_FIELDS);
        validateSection(registry, "requirements", REQUIRED_REQUIREMENT_FIELDS);
        validateSection(registry, "ruleInventory", REQUIRED_RULE_FIELDS);

        Set<String> requirementIds = idsInSection(registry, "requirements");
        assertExpectedRange(requirementIds, "A", 1, 26);
        assertExpectedRange(requirementIds, "S", 1, 11);
        assertExpectedRange(requirementIds, "FE", 1, 12);
        assertExpectedRange(requirementIds, "SA", 1, 12);
        assertExpectedRange(requirementIds, "T", 1, 28);

        assertFalse(registry.contains("runtime-shell: 3.0.11\n  active"),
            "Runtime Shell 3.0.11 must remain non-guiding in Phase 0");
        assertNoForbiddenPhase0Status(entriesInSection(registry, "requirements"));
        assertNoForbiddenPhase0Status(entriesInSection(registry, "ruleInventory"));
    }

    @Test
    void schemaDeclaresCompletenessFieldsAndForbiddenPhase0Statuses() throws IOException {
        String schema = Files.readString(SCHEMA);

        for (String field : REQUIRED_REQUIREMENT_FIELDS) {
            assertTrue(schema.contains("  - " + field), "schema must require requirement field " + field);
        }
        for (String field : REQUIRED_RULE_FIELDS) {
            assertTrue(schema.contains("  - " + field), "schema must require rule inventory field " + field);
        }
        for (String status : FORBIDDEN_PHASE0_STATUSES) {
            assertTrue(schema.contains("  - " + status), "schema must forbid Phase 0 status " + status);
        }
    }

    @Test
    void completenessCheckRejectsMissingRequirementEvidence() {
        String invalidRegistry = """
            requirements:
              - id: A-1
                owner: runtime
                tool: java-archunit
                test: future-a1-contract
                status: missing-blocking
            """;

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> validateSection(invalidRegistry, "requirements", REQUIRED_REQUIREMENT_FIELDS));

        assertTrue(error.getMessage().contains("evidence"));
    }

    @Test
    void completenessCheckRejectsMissingRuleTargetCount() {
        String invalidRegistry = """
            ruleInventory:
              - id: java-host-profile
                kind: java
                wired: true
                scope: host-profile
                test: HostUltraThinRuleTest
                status: partial
                evidence: HostProfile.java
            """;

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> validateSection(invalidRegistry, "ruleInventory", REQUIRED_RULE_FIELDS));

        assertTrue(error.getMessage().contains("targetCount"));
    }

    @Test
    void completenessCheckRejectsPassingStatusDuringPhase0() {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("id", "A-1");
        entry.put("owner", "runtime");
        entry.put("tool", "java-archunit");
        entry.put("test", "future-a1-contract");
        entry.put("status", "enforced-passing");
        entry.put("evidence", "not-accepted-in-phase0");

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> assertNoForbiddenPhase0Status(List.of(entry)));

        assertTrue(error.getMessage().contains("enforced-passing"));
    }

    @Test
    void productionRegistryPassesCompletenessCheck() {
        assertDoesNotThrow(() -> {
            String registry = Files.readString(REGISTRY);
            validateSection(registry, "requirements", REQUIRED_REQUIREMENT_FIELDS);
            validateSection(registry, "ruleInventory", REQUIRED_RULE_FIELDS);
        });
    }

    private static void validateSection(String yaml, String section, List<String> requiredFields) {
        List<Map<String, String>> entries = entriesInSection(yaml, section);
        if (entries.isEmpty()) {
            throw new IllegalStateException("Registry section has no entries: " + section);
        }

        for (Map<String, String> entry : entries) {
            for (String field : requiredFields) {
                if (!entry.containsKey(field) || entry.get(field).isBlank()) {
                    throw new IllegalStateException(
                        "Registry entry " + entry.getOrDefault("id", "<missing-id>")
                            + " is missing " + field);
                }
            }
        }
    }

    private static Set<String> idsInSection(String yaml, String section) {
        Set<String> ids = new LinkedHashSet<>();
        for (Map<String, String> entry : entriesInSection(yaml, section)) {
            ids.add(entry.get("id"));
        }
        return ids;
    }

    private static List<Map<String, String>> entriesInSection(String yaml, String section) {
        List<Map<String, String>> entries = new ArrayList<>();
        Map<String, String> current = null;
        boolean inSection = false;
        Pattern itemPattern = Pattern.compile("^  - id: (.+)$");
        Pattern fieldPattern = Pattern.compile("^    ([A-Za-z][A-Za-z0-9]*): (.*)$");

        for (String line : yaml.split("\\R")) {
            if (line.equals(section + ":")) {
                inSection = true;
                current = null;
                continue;
            }
            if (inSection && !line.startsWith(" ") && line.endsWith(":")) {
                break;
            }
            if (!inSection) {
                continue;
            }

            Matcher itemMatcher = itemPattern.matcher(line);
            if (itemMatcher.matches()) {
                current = new LinkedHashMap<>();
                current.put("id", itemMatcher.group(1).trim());
                entries.add(current);
                continue;
            }

            Matcher fieldMatcher = fieldPattern.matcher(line);
            if (current != null && fieldMatcher.matches()) {
                current.put(fieldMatcher.group(1), fieldMatcher.group(2).trim());
            }
        }

        return entries;
    }

    private static void assertExpectedRange(Set<String> actualIds, String prefix, int start, int end) {
        for (int index = start; index <= end; index++) {
            String expected = prefix + "-" + index;
            assertTrue(actualIds.contains(expected), "registry must contain " + expected);
        }
        assertEquals(end - start + 1, actualIds.stream()
            .filter(id -> id.startsWith(prefix + "-"))
            .count(), "registry must not duplicate or over-expand " + prefix + " range");
    }

    private static void assertNoForbiddenPhase0Status(List<Map<String, String>> entries) {
        for (Map<String, String> entry : entries) {
            String status = entry.get("status");
            if (FORBIDDEN_PHASE0_STATUSES.contains(status)) {
                throw new IllegalStateException(
                    "Phase 0 registry entry " + entry.get("id") + " must not use status " + status);
            }
        }
    }
}
