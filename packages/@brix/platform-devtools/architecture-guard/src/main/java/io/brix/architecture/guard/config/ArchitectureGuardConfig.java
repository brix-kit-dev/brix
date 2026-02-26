/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.config;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Architecture Guard Configuration.
 *
 * <p>Centralized configuration for architecture rules to avoid hardcoding
 * and enable easy customization for different projects.</p>
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><b>Whitelist over Blacklist</b> - Define what IS allowed, not what is forbidden</li>
 *   <li><b>Convention over Configuration</b> - Use naming conventions that are hard to bypass</li>
 *   <li><b>Discoverable</b> - Auto-discover plugins by interface implementation, not package name</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <p>Override via system properties or configuration file:</p>
 * <pre>{@code
 * -Dbrix.architecture.plugin-pattern=com\.mycompany\.app\.[a-z]+
 * }</pre>
 *
 * @author Brix Architecture Team
 * @since 3.1.0
 */
public final class ArchitectureGuardConfig {

    private ArchitectureGuardConfig() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== Plugin Package Configuration ====================

    /**
     * Pattern to identify plugin packages.
     *
     * <p>Default: {@code com.shinwa.app.<plugin-name>} or {@code io.brix.app.<plugin-name>}</p>
     *
     * <p>Can be overridden via system property: {@code brix.architecture.plugin-pattern}</p>
     */
    public static final Pattern PLUGIN_PACKAGE_PATTERN = Pattern.compile(
            System.getProperty("brix.architecture.plugin-pattern",
                    "^(com\\.shinwa\\.app\\.[a-z]+|io\\.brix\\.app\\.[a-z]+)"));

    // ==================== Allowed Dependencies (Whitelist) ====================

    /**
     * Packages that plugins ARE allowed to depend on.
     *
     * <p>Anything NOT in this whitelist from external sources is suspicious.</p>
     */
    public static final Set<String> ALLOWED_PLUGIN_DEPENDENCIES;

    static {
        Set<String> allowed = new HashSet<>();

        // Java standard library
        allowed.add("java..");
        allowed.add("javax..");
        allowed.add("jakarta..");

        // Runtime SDK API (the ONLY allowed Brix dependency for plugins)
        allowed.add("io.runtime.sdk..");

        // Common safe libraries
        allowed.add("org.slf4j..");
        allowed.add("lombok..");

        // Spring annotations ONLY (not implementations)
        allowed.add("org.springframework.stereotype..");
        allowed.add("org.springframework.beans.factory.annotation..");

        ALLOWED_PLUGIN_DEPENDENCIES = Collections.unmodifiableSet(allowed);
    }

    // ==================== Forbidden Patterns (Blacklist - Defense in Depth) ====================

    /**
     * Packages that plugins must NEVER depend on, regardless of whitelist.
     *
     * <p>These are infrastructure concerns that must go through Capability interfaces.</p>
     */
    public static final Set<String> FORBIDDEN_PLUGIN_DEPENDENCIES;

    static {
        Set<String> forbidden = new HashSet<>();

        // Infrastructure adapters
        forbidden.add("io.brix.infra.adapter..");
        forbidden.add("io.infra.adapter..");

        // Direct middleware clients
        forbidden.add("org.springframework.kafka..");
        forbidden.add("org.apache.kafka..");
        forbidden.add("org.springframework.data.redis..");
        forbidden.add("redis.clients..");
        forbidden.add("io.lettuce..");
        forbidden.add("org.springframework.amqp..");
        forbidden.add("com.rabbitmq..");

        // Direct HTTP clients
        forbidden.add("org.springframework.web.client..");
        forbidden.add("org.springframework.web.reactive.function.client..");
        forbidden.add("org.springframework.cloud.openfeign..");
        forbidden.add("com.squareup.okhttp3..");
        forbidden.add("java.net.http..");
        forbidden.add("org.apache.http..");

        // Spring container internals
        forbidden.add("org.springframework.context.ApplicationContext");
        forbidden.add("org.springframework.beans.factory.BeanFactory");
        forbidden.add("org.springframework.context.ApplicationEventPublisher");

        // File system / IO (should use FileStorageCapability)
        forbidden.add("java.io.File");
        forbidden.add("java.nio.file..");

        // Direct database access (should use DatabaseCapability)
        forbidden.add("java.sql..");
        forbidden.add("org.springframework.jdbc..");
        forbidden.add("org.hibernate..");

        FORBIDDEN_PLUGIN_DEPENDENCIES = Collections.unmodifiableSet(forbidden);
    }

    // ==================== Layer Naming Conventions ====================

    /**
     * Required package structure for plugin modules.
     *
     * <p>Plugins MUST follow this structure for rules to work correctly.</p>
     */
    public static final String[] REQUIRED_LAYER_PACKAGES = {
            "domain",    // Domain entities and value objects
            "service",   // Application/domain services  
            "repository" // Repository interfaces (not implementations!)
    };

    /**
     * Packages that indicate infrastructure concern (should be in -server, not -core).
     */
    public static final String[] INFRASTRUCTURE_LAYER_PACKAGES = {
            "controller",  // REST controllers
            "adapter",     // Adapter implementations
            "config",      // Configuration classes
            "persistence"  // JPA/database implementations
    };

    // ==================== Marker Interfaces for Discovery ====================

    /**
     * Marker interface/annotation that all plugins must implement.
     *
     * <p>Using marker interface is MORE RELIABLE than package name matching!</p>
     */
    public static final String PLUGIN_MARKER_ANNOTATION = "io.runtime.sdk.plugin.BrixPlugin";

    /**
     * Marker interface for Capability interfaces.
     */
    public static final String CAPABILITY_MARKER_INTERFACE = "io.runtime.sdk.capability.Capability";

    // ==================== Validation Methods ====================

    /**
     * Check if a package is a valid plugin package.
     *
     * @param packageName the package name to check
     * @return true if matches plugin pattern
     */
    public static boolean isPluginPackage(String packageName) {
        return PLUGIN_PACKAGE_PATTERN.matcher(packageName).find();
    }

    /**
     * Check if a dependency is forbidden for plugins.
     *
     * @param dependencyPackage the dependency package name
     * @return true if dependency is forbidden
     */
    public static boolean isForbiddenDependency(String dependencyPackage) {
        return FORBIDDEN_PLUGIN_DEPENDENCIES.stream()
                .anyMatch(forbidden -> matchesPackagePattern(dependencyPackage, forbidden));
    }

    /**
     * Check if a dependency is explicitly allowed for plugins.
     *
     * @param dependencyPackage the dependency package name
     * @return true if dependency is allowed
     */
    public static boolean isAllowedDependency(String dependencyPackage) {
        return ALLOWED_PLUGIN_DEPENDENCIES.stream()
                .anyMatch(allowed -> matchesPackagePattern(dependencyPackage, allowed));
    }

    /**
     * Match package against ArchUnit-style pattern (with `..` wildcard).
     */
    private static boolean matchesPackagePattern(String packageName, String pattern) {
        if (pattern.endsWith("..")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            return packageName.startsWith(prefix) || packageName.equals(prefix.substring(0, prefix.length() - 1));
        }
        return packageName.equals(pattern) || packageName.startsWith(pattern + ".");
    }
}
