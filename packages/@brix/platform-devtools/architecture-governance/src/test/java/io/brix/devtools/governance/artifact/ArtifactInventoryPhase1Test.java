/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.artifact;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtifactInventoryPhase1Test {

    @TempDir
    private Path root;

    @Test
    void inventoryUsesModuleKindDescriptorAndProviderToCompileExecutionPlan() throws IOException {
        mavenArtifact("packages/@brix/apps/invoice/invoice-api", "io.brix.app", "invoice-api",
            "plugin-api", List.of());
        mavenArtifact("packages/@brix/apps/invoice/invoice-core", "io.brix.app", "invoice-core",
            "plugin-core", List.of("io.brix.app:invoice-api:1.0.0"));
        Path server = mavenArtifact("packages/@brix/apps/invoice/invoice-server", "io.brix.app",
            "invoice-server", "plugin-server", List.of("io.brix.app:invoice-core:1.0.0"));
        write(server.resolve("src/main/resources/META-INF/brix/plugin-manifest.yaml"),
            "apiVersion: brix.io/v1\nkind: Plugin\n");
        write(server.resolve("src/main/resources/META-INF/services/io.runtime.sdk.plugin.BrixPlugin"),
            "io.brix.app.invoice.InvoicePlugin\n");

        Path operational = mavenArtifact("packages/@brix/platform-commons/platform-admin",
            "io.brix.platform", "platform-admin", "platform-operational", List.of());
        write(operational.resolve("src/main/resources/META-INF/brix/platform-operational.yaml"),
            "apiVersion: brix.io/platform-operational/v1\nkind: PlatformOperationalModule\n");
        write(operational.resolve(
            "src/main/resources/META-INF/services/io.runtime.orchestrator.operational.PlatformOperationalModule"),
            "io.brix.platform.admin.PlatformAdminOperationalModule\n");

        ArtifactInventory inventory = new ArtifactInventoryBuilder().build(root, true);

        assertDoesNotThrow(() -> new DescriptorAssociationVerifier().verify(inventory));
        assertDoesNotThrow(() -> new ArtifactGraphVerifier().verify(inventory));
        List<ExecutionPlan> plans = new ExecutionPlanCompiler().compile(inventory);
        assertEquals(4, plans.size());
        assertTrue(plans.stream().anyMatch(plan ->
            plan.coordinate().artifactId().equals("invoice-server")
                && plan.executors().contains("java-static-boundary")
                && plan.executors().contains("plugin-descriptor")));
        assertTrue(plans.stream().anyMatch(plan ->
            plan.coordinate().artifactId().equals("platform-admin")
                && plan.executors().contains("java-static-boundary")
                && plan.executors().contains("operational-descriptor")));
    }

    @Test
    void javaStaticBoundaryExecutorCoversEveryApplicableMavenModuleKind() {
        for (ModuleKind moduleKind : ModuleKind.values()) {
            ArtifactNode artifact = new ArtifactNode(
                new ArtifactCoordinate("io.brix.test", moduleKind.wireName(), "1.0.0"),
                moduleKind,
                root.resolve(moduleKind.wireName()),
                Map.of(),
                Map.of(),
                Set.of());
            List<ExecutionPlan> plans = new ExecutionPlanCompiler().compile(new ArtifactInventory(List.of(artifact)));

            assertEquals(moduleKind.isJavaStaticBoundaryTarget(),
                plans.get(0).executors().contains("java-static-boundary"),
                moduleKind.wireName());
        }
    }

    @Test
    void missingModuleKindFailsEvenWhenArtifactNameLooksLikePluginCore() throws IOException {
        Path base = root.resolve("packages/@brix/apps/invoice/invoice-core");
        write(base.resolve("pom.xml"), pom("io.brix.app", "invoice-core", null, List.of()));

        ArchitectureGovernanceException error = assertThrows(ArchitectureGovernanceException.class,
            () -> new ArtifactInventoryBuilder().build(root, true));

        assertTrue(error.getMessage().contains("Missing moduleKind"));
    }

    @Test
    void descriptorProviderAssociationRejectsMixedDescriptorKinds() throws IOException {
        Path server = mavenArtifact("packages/@brix/apps/invoice/invoice-server", "io.brix.app",
            "invoice-server", "plugin-server", List.of());
        write(server.resolve("src/main/resources/META-INF/brix/plugin-manifest.yaml"),
            "kind: Plugin\n");
        write(server.resolve("src/main/resources/META-INF/brix/platform-operational.yaml"),
            "kind: PlatformOperationalModule\n");
        write(server.resolve("src/main/resources/META-INF/services/io.runtime.sdk.plugin.BrixPlugin"),
            "io.brix.app.invoice.InvoicePlugin\n");

        ArtifactInventory inventory = new ArtifactInventoryBuilder().build(root, true);

        ArchitectureGovernanceException error = assertThrows(ArchitectureGovernanceException.class,
            () -> new DescriptorAssociationVerifier().verify(inventory));
        assertTrue(error.getMessage().contains("must not contain both"));
    }

    @Test
    void graphRejectsPluginCoreDependencyOnAdapter() throws IOException {
        mavenArtifact("packages/@brix/infra/adapter-email", "io.brix.infra", "adapter-email",
            "adapter", List.of());
        mavenArtifact("packages/@brix/apps/invoice/invoice-core", "io.brix.app", "invoice-core",
            "plugin-core", List.of("io.brix.infra:adapter-email:1.0.0"));

        ArtifactInventory inventory = new ArtifactInventoryBuilder().build(root, true);

        ArchitectureGovernanceException error = assertThrows(ArchitectureGovernanceException.class,
            () -> new ArtifactGraphVerifier().verify(inventory));
        assertTrue(error.getMessage().contains("plugin-core must not depend on adapter"));
    }

    @Test
    void graphRejectsArtifactCycle() throws IOException {
        mavenArtifact("packages/@brix/apps/invoice/invoice-api", "io.brix.app", "invoice-api",
            "plugin-api", List.of("io.brix.app:invoice-contract:1.0.0"));
        mavenArtifact("packages/@brix/apps/invoice/invoice-contract", "io.brix.app", "invoice-contract",
            "shared-contract", List.of("io.brix.app:invoice-api:1.0.0"));

        ArtifactInventory inventory = new ArtifactInventoryBuilder().build(root, true);

        ArchitectureGovernanceException error = assertThrows(ArchitectureGovernanceException.class,
            () -> new ArtifactGraphVerifier().verify(inventory));
        assertTrue(error.getMessage().contains("cycle"));
    }

    private Path mavenArtifact(
        String relativePath,
        String groupId,
        String artifactId,
        String moduleKind,
        List<String> dependencies) throws IOException {
        Path base = root.resolve(relativePath);
        write(base.resolve("pom.xml"), pom(groupId, artifactId, moduleKind, dependencies));
        return base;
    }

    private static String pom(
        String groupId,
        String artifactId,
        String moduleKind,
        List<String> dependencies) {
        String property = moduleKind == null ? "" : """
              <properties>
                <brix.moduleKind>%s</brix.moduleKind>
              </properties>
            """.formatted(moduleKind);
        StringBuilder deps = new StringBuilder();
        for (String dependency : dependencies) {
            String[] parts = dependency.split(":");
            deps.append("""
                  <dependency>
                    <groupId>%s</groupId>
                    <artifactId>%s</artifactId>
                    <version>%s</version>
                  </dependency>
                """.formatted(parts[0], parts[1], parts[2]));
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>%s</groupId>
              <artifactId>%s</artifactId>
              <version>1.0.0</version>
            %s
              <dependencies>
            %s
              </dependencies>
            </project>
            """.formatted(groupId, artifactId, property, deps);
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
