/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import io.runtime.orchestrator.event.EventPolicies;

/**
 * The schemas of endpoint contracts, read from the class path and compiled once (root BRX-RS-MANIFEST-022;
 * standard-link plan L7.2, L7.3). A {@code brix://schemas/<plugin>/<name>/<version>} reference, and each {@code $ref}
 * it makes to another one, resolves to {@code META-INF/brix/schemas/<plugin>/<name>/<version>.json}.
 */
final class EndpointSchemas {

    private final ClassLoader classes;
    private final ObjectMapper json;
    private final SchemaRegistry registry;
    private final Map<String, Optional<Compiled>> compiled = new ConcurrentHashMap<>();

    EndpointSchemas(ClassLoader classes, ObjectMapper json) {
        this.classes = Objects.requireNonNull(classes, "classes must not be null");
        this.json = Objects.requireNonNull(json, "json must not be null");
        this.registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
            builder -> builder.schemas(this::source));
    }

    /**
     * A compiled schema and its document.
     *
     * @param schema the compiled schema
     * @param node the schema document, whose top-level properties type the query and path parameters
     */
    record Compiled(Schema schema, JsonNode node) {
    }

    /**
     * One rule an instance breaks.
     *
     * @param path where, as a JSON path into the instance
     * @param rule the schema keyword, such as {@code required} or {@code maximum}
     */
    record Violation(String path, String rule) {
    }

    /**
     * Returns the compiled schema of a reference.
     *
     * @param schemaRef the manifest's {@code schemaRef}
     * @return the schema, or empty when the reference is blank or names no schema on the class path
     */
    Optional<Compiled> schema(String schemaRef) {
        if (schemaRef == null || schemaRef.isBlank()) {
            return Optional.empty();
        }
        return compiled.computeIfAbsent(schemaRef, ref -> Optional.ofNullable(source(ref)).map(text -> {
            JsonNode node = json.readTree(text);
            return new Compiled(registry.getSchema(SchemaLocation.of(ref), node), node);
        }));
    }

    /**
     * Returns the rules an instance breaks, without its values.
     *
     * @param schema the schema
     * @param instance the instance
     * @return the violations, empty when it is valid
     */
    static List<Violation> violations(Compiled schema, JsonNode instance) {
        return schema.schema().validate(instance).stream()
            .map(error -> new Violation(error.getInstanceLocation().toString(), error.getKeyword()))
            .distinct()
            .toList();
    }

    private String source(String iri) {
        String resource = EventPolicies.schemaResource(iri);
        if (resource == null) {
            return null;
        }
        try (InputStream in = classes.getResourceAsStream(resource)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("cannot read " + resource, unreadable);
        }
    }
}
