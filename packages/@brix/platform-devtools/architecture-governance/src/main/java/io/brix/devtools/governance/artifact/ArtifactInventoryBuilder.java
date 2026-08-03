/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.artifact;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Builds a source-tree inventory from Maven POM and package metadata.
 */
public final class ArtifactInventoryBuilder {

    private static final Pattern PACKAGE_NAME =
        Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PACKAGE_VERSION =
        Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PACKAGE_MODULE_KIND =
        Pattern.compile("\"moduleKind\"\\s*:\\s*\"([^\"]+)\"");

    public ArtifactInventory build(Path root, boolean requireModuleKindForBrixArtifacts) {
        try {
            List<ArtifactNode> nodes = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path path : paths
                    .filter(Files::isRegularFile)
                    .filter(ArtifactInventoryBuilder::isBuildDescriptor)
                    .filter(ArtifactInventoryBuilder::isNotBuildOutput)
                    .toList()) {
                    if (path.getFileName().toString().equals("pom.xml")) {
                        readPomArtifact(path, requireModuleKindForBrixArtifacts).ifPresent(nodes::add);
                    } else if (path.getFileName().toString().equals("package.json")) {
                        readPackageArtifact(path, requireModuleKindForBrixArtifacts).ifPresent(nodes::add);
                    }
                }
            }
            return new ArtifactInventory(nodes);
        } catch (IOException ex) {
            throw new ArchitectureGovernanceException("Unable to scan artifact inventory: " + ex.getMessage());
        }
    }

    private static Optional<ArtifactNode> readPomArtifact(
        Path pom,
        boolean requireModuleKindForBrixArtifacts) {
        Document document = parseXml(pom);
        Element project = document.getDocumentElement();
        String groupId = directText(project, "groupId")
            .or(() -> child(project, "parent").flatMap(parent -> directText(parent, "groupId")))
            .orElse("");
        String artifactId = directText(project, "artifactId").orElse("");
        String version = directText(project, "version")
            .or(() -> child(project, "parent").flatMap(parent -> directText(parent, "version")))
            .orElse("0.0.0");
        Optional<String> moduleKind = child(project, "properties").flatMap(properties ->
            directText(properties, "brix.moduleKind").or(() -> directText(properties, "moduleKind")));

        boolean brixArtifact = groupId.startsWith("io.brix") || artifactId.startsWith("brix-")
            || pom.toString().contains("/packages/@brix/");
        if (moduleKind.isEmpty()) {
            if (requireModuleKindForBrixArtifacts && brixArtifact) {
                throw new ArchitectureGovernanceException(
                    "Missing moduleKind for Brix Maven artifact " + groupId + ":" + artifactId);
            }
            return Optional.empty();
        }

        Path base = pom.getParent();
        return Optional.of(new ArtifactNode(
            new ArtifactCoordinate(groupId, artifactId, version),
            ModuleKind.fromWireName(moduleKind.get()),
            base,
            scanDescriptors(base),
            scanProviders(base),
            readDependencies(project)));
    }

    private static Optional<ArtifactNode> readPackageArtifact(
        Path packageJson,
        boolean requireModuleKindForBrixArtifacts) throws IOException {
        String json = Files.readString(packageJson);
        Optional<String> name = match(PACKAGE_NAME, json);
        Optional<String> version = match(PACKAGE_VERSION, json);
        Optional<String> moduleKind = match(PACKAGE_MODULE_KIND, json);
        boolean brixArtifact = name.orElse("").startsWith("@brix")
            || packageJson.toString().contains("/packages/@brix/");
        if (moduleKind.isEmpty()) {
            if (requireModuleKindForBrixArtifacts && brixArtifact) {
                throw new ArchitectureGovernanceException(
                    "Missing moduleKind for Brix package artifact " + name.orElse(packageJson.toString()));
            }
            return Optional.empty();
        }

        String packageName = name.orElseThrow(() ->
            new ArchitectureGovernanceException("Missing package name in " + packageJson));
        Path base = packageJson.getParent();
        return Optional.of(new ArtifactNode(
            new ArtifactCoordinate("npm", packageName, version.orElse("0.0.0")),
            ModuleKind.fromWireName(moduleKind.get()),
            base,
            scanDescriptors(base),
            scanProviders(base),
            Set.of()));
    }

    private static Set<ArtifactCoordinate> readDependencies(Element project) {
        Set<ArtifactCoordinate> dependencies = new LinkedHashSet<>();
        child(project, "dependencies").ifPresent(dependenciesElement -> {
            var nodes = dependenciesElement.getChildNodes();
            for (int index = 0; index < nodes.getLength(); index++) {
                if (nodes.item(index) instanceof Element dependency
                    && dependency.getTagName().equals("dependency")) {
                    String groupId = directText(dependency, "groupId").orElse("");
                    String artifactId = directText(dependency, "artifactId").orElse("");
                    String version = directText(dependency, "version").orElse("");
                    if (!groupId.isBlank() && !artifactId.isBlank()) {
                        dependencies.add(new ArtifactCoordinate(groupId, artifactId, version));
                    }
                }
            }
        });
        return dependencies;
    }

    private static Map<DescriptorKind, Set<Path>> scanDescriptors(Path base) {
        Map<DescriptorKind, Set<Path>> descriptors = new EnumMap<>(DescriptorKind.class);
        for (DescriptorKind kind : DescriptorKind.values()) {
            descriptors.put(kind, new LinkedHashSet<>());
        }
        addIfExists(descriptors, DescriptorKind.PLUGIN_MANIFEST,
            base.resolve("src/main/resources").resolve(DescriptorKind.PLUGIN_MANIFEST.fixedPath()));
        addIfExists(descriptors, DescriptorKind.PLATFORM_OPERATIONAL,
            base.resolve("src/main/resources").resolve(DescriptorKind.PLATFORM_OPERATIONAL.fixedPath()));
        addIfExists(descriptors, DescriptorKind.UI_MANIFEST,
            base.resolve(DescriptorKind.UI_MANIFEST.fixedPath()));
        return descriptors;
    }

    private static Map<ServiceProviderKind, Set<Path>> scanProviders(Path base) {
        Map<ServiceProviderKind, Set<Path>> providers = new EnumMap<>(ServiceProviderKind.class);
        for (ServiceProviderKind kind : ServiceProviderKind.values()) {
            providers.put(kind, new LinkedHashSet<>());
            addIfExists(providers, kind, base.resolve("src/main/resources").resolve(kind.servicePath()));
        }
        return providers;
    }

    private static <K> void addIfExists(Map<K, Set<Path>> map, K key, Path path) {
        if (Files.isRegularFile(path)) {
            map.get(key).add(path);
        }
    }

    private static boolean isBuildDescriptor(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.equals("pom.xml") || fileName.equals("package.json");
    }

    private static boolean isNotBuildOutput(Path path) {
        return !path.toString().contains("/target/") && !path.toString().contains("/node_modules/");
    }

    private static Document parseXml(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(input);
        } catch (Exception ex) {
            throw new ArchitectureGovernanceException("Unable to parse POM " + path + ": " + ex.getMessage());
        }
    }

    private static Optional<Element> child(Element element, String name) {
        var nodes = element.getChildNodes();
        for (int index = 0; index < nodes.getLength(); index++) {
            if (nodes.item(index) instanceof Element child && child.getTagName().equals(name)) {
                return Optional.of(child);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> directText(Element element, String name) {
        return child(element, name).map(Element::getTextContent).map(String::trim).filter(value -> !value.isBlank());
    }

    private static Optional<String> match(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
