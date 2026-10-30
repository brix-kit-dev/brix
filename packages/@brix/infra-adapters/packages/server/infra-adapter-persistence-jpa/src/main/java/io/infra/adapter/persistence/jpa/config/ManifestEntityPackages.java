/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.persistence.autoconfigure.EntityScanPackages;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;

import io.runtime.manifest.loader.PluginManifestLoader;
import io.runtime.manifest.model.PluginManifest;
import io.runtime.manifest.validation.PluginManifestValidator;
import io.runtime.sdk.capability.PersistenceCapability;

/**
 * Registers the entities of every Owner whose manifest declares {@link PersistenceCapability} (root
 * BRX-RS-DATA-027; Kafka design §14 K18), so a plugin needs no Spring {@code @EntityScan} of its own.
 *
 * <p>An Owner's classes are those packaged with its manifest and those of the artifacts its manifest lists
 * under {@code modules}, the same rule the owner directory uses to tell which Owner a class belongs to.
 * Their packages that hold a JPA entity, embeddable, mapped superclass or converter are added to the
 * entity scan. When no {@code @EntityScan} had been declared, the Host's auto-configuration packages stay
 * scanned too, as Spring Boot would have scanned them.</p>
 *
 * @author Brix Platform Authors
 * @since 3.3.0
 */
public final class ManifestEntityPackages implements BeanDefinitionRegistryPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ManifestEntityPackages.class);

    private static final List<String> MAPPING_ANNOTATIONS = List.of(
        "jakarta.persistence.Entity",
        "jakarta.persistence.Embeddable",
        "jakarta.persistence.MappedSuperclass",
        "jakarta.persistence.Converter");

    private final ClassLoader classLoader;

    /**
     * Creates the registrar.
     *
     * @param classLoader where the plugin manifests and classes are
     */
    public ManifestEntityPackages(ClassLoader classLoader) {
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader must not be null");
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        Set<String> packages = entityPackages(classLoader);
        if (packages.isEmpty()) {
            return;
        }
        if (!registry.containsBeanDefinition(EntityScanPackages.class.getName())
                && registry instanceof BeanFactory beanFactory && AutoConfigurationPackages.has(beanFactory)) {
            packages.addAll(AutoConfigurationPackages.get(beanFactory));
        }
        log.info("event=persistence.entity_packages packages={}", packages);
        EntityScanPackages.register(registry, packages);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    /**
     * Returns the packages that hold the mapped types of the Owners declaring persistence.
     *
     * @param classLoader where the plugin manifests and classes are
     * @return package names
     */
    static Set<String> entityPackages(ClassLoader classLoader) {
        PluginManifestLoader loader = new PluginManifestLoader();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classLoader);
        MetadataReaderFactory readers = new CachingMetadataReaderFactory(resolver);
        Set<String> packages = new TreeSet<>();
        try {
            for (URL resource : Collections.list(classLoader.getResources(PluginManifestValidator.ACTIVE_MANIFEST_RESOURCE))) {
                PluginManifest manifest;
                try (InputStream input = resource.openStream()) {
                    manifest = loader.load(input);
                }
                if (!declaresPersistence(manifest)) {
                    continue;
                }
                for (String root : roots(classLoader, resource, manifest)) {
                    for (Resource type : resolver.getResources(root + "**/*.class")) {
                        AnnotationMetadata metadata = readers.getMetadataReader(type).getAnnotationMetadata();
                        if (MAPPING_ANNOTATIONS.stream().anyMatch(metadata::hasAnnotation)) {
                            packages.add(ClassUtils.getPackageName(metadata.getClassName()));
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read the plugin manifests for their entities", e);
        }
        return packages;
    }

    private static Set<String> roots(ClassLoader classLoader, URL manifestResource, PluginManifest manifest)
            throws IOException {
        Set<String> roots = new LinkedHashSet<>();
        roots.add(rootOf(manifestResource, PluginManifestValidator.ACTIVE_MANIFEST_RESOURCE));
        List<PluginManifest.Module> modules = manifest.getModules() == null ? List.of() : manifest.getModules();
        for (PluginManifest.Module module : modules) {
            if (module.getGroupId() == null || module.getArtifactId() == null) {
                continue;
            }
            String pom = "META-INF/maven/" + module.getGroupId() + "/" + module.getArtifactId() + "/pom.properties";
            for (URL found : new ArrayList<>(Collections.list(classLoader.getResources(pom)))) {
                roots.add(rootOf(found, pom));
            }
        }
        return roots;
    }

    private static String rootOf(URL resource, String path) {
        String url = resource.toExternalForm();
        return url.substring(0, url.length() - path.length());
    }

    private static boolean declaresPersistence(PluginManifest manifest) {
        if (manifest.getCapabilities() == null) {
            return false;
        }
        return declares(manifest.getCapabilities().getRequired()) || declares(manifest.getCapabilities().getOptional());
    }

    private static boolean declares(List<PluginManifest.CapabilityRef> references) {
        return references != null && references.stream().filter(Objects::nonNull).map(PluginManifest.CapabilityRef::getId)
            .anyMatch(id -> PersistenceCapability.class.getName().equals(id)
                || PersistenceCapability.class.getSimpleName().equals(id));
    }
}
