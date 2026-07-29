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
package io.brix.devtools.cleanbaseline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.brix.devtools.cleanbaseline.CleanBaselinePhase0Scanner.FindingDisposition;
import io.brix.devtools.cleanbaseline.CleanBaselinePhase0Scanner.ScanReport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CleanBaselinePhase0ScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void classifiesProductionLegacyTenantApiAsBlocking() throws IOException {
        write("packages/@brix/enterprise-host/host-shell-standalone/pom.xml",
            "<artifactId>infra-adapter-kafka-outbox</artifactId>\n");
        write("packages/@brix/enterprise-host/host-contract-tests/src/test/java/ContractTest.java",
            "dispatcher.invoke(\"GET\", \"/api/v1/tenants\", null);\n");
        write("docs/v3.0.10-clean-baseline-plan.md",
            "Legacy path `/api/v1/tenants` is retained as evidence.\n");

        ScanReport report = CleanBaselinePhase0Scanner.scan(List.of(tempDir));

        assertEquals(1, report.count(FindingDisposition.BLOCKING));
        assertEquals(1, report.count(FindingDisposition.REVIEW));
        assertEquals(1, report.count(FindingDisposition.RETAIN));
    }

    @Test
    void retainsFrontendRuntimeContextAndBlocksScaffoldingTemplates() throws IOException {
        write("packages/@brix/runtime-sdk/runtime-sdk-api-web/src/index.ts",
            "export interface RuntimeContext {}\n");
        write("packages/@brix/platform-devtools/@brix/create-brix/templates/backend/core/ExampleService.java.ejs",
            "import io.runtime.sdk.context.RuntimeContext;\n");

        ScanReport report = CleanBaselinePhase0Scanner.scan(List.of(tempDir));

        assertEquals(1, report.count(FindingDisposition.RETAIN));
        assertEquals(1, report.count(FindingDisposition.BLOCKING));
        assertTrue(report.findings().stream()
            .anyMatch(finding -> finding.reason().contains("frontend runtime context")));
    }

    @Test
    void recordsCleanScopeAndTargetComposition() throws IOException {
        write("src/main/java/Bootstrap.java",
            "class Bootstrap implements org.springframework.boot.ApplicationRunner {}\n");

        ScanReport report = CleanBaselinePhase0Scanner.scan(List.of(tempDir));

        assertTrue(report.cleanInitializationScope().contains("pre-release clean initialization"));
        assertTrue(report.targetHostComposition().contains("Runtime Shell 3.0.10"));
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
