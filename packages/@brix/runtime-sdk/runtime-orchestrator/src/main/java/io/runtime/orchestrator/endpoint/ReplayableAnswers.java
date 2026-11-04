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
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import io.runtime.orchestrator.event.EventPolicies;

/**
 * What the runtime may keep of an endpoint's answer (root BRX-RS-SECURITY-008; idempotency design §4.6, D74): an answer
 * with a {@code secret} or {@code credential} field is not kept, so an endpoint that declares such a response cannot
 * have its results replayed. The check reads the response schema and every {@code brix://schemas} schema it refers to.
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class ReplayableAnswers {

    private static final String CLASSIFICATION = "x-brix-classification";
    private static final String REFERENCE = "$ref";
    private static final Set<String> NOT_KEPT = Set.of("secret", "credential");
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private ReplayableAnswers() {
    }

    /**
     * Finds a field of a response schema whose data class the runtime does not keep.
     *
     * @param schemaRef the response's {@code brix://schemas/...} reference
     * @param classes the class path of the schemas
     * @return where the field is declared, if there is one
     */
    public static Optional<String> fieldNotKept(String schemaRef, ClassLoader classes) {
        return find(schemaRef, classes, new HashSet<>());
    }

    private static Optional<String> find(String schemaRef, ClassLoader classes, Set<String> seen) {
        String resource = EventPolicies.schemaResource(schemaRef);
        if (resource == null || !seen.add(schemaRef)) {
            return Optional.empty();
        }
        try (InputStream in = classes.getResourceAsStream(resource)) {
            return in == null ? Optional.empty() : find(JSON.readTree(in), schemaRef, classes, seen);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("cannot read " + resource, unreadable);
        }
    }

    private static Optional<String> find(JsonNode node, String path, ClassLoader classes, Set<String> seen) {
        if (node.isArray()) {
            for (JsonNode element : node) {
                Optional<String> found = find(element, path, classes, seen);
                if (found.isPresent()) {
                    return found;
                }
            }
            return Optional.empty();
        }
        if (!node.isObject()) {
            return Optional.empty();
        }
        if (NOT_KEPT.contains(node.path(CLASSIFICATION).asString("").toLowerCase(Locale.ROOT))) {
            return Optional.of(path);
        }
        for (Map.Entry<String, JsonNode> member : node.properties()) {
            Optional<String> found = REFERENCE.equals(member.getKey()) && member.getValue().isString()
                ? find(member.getValue().asString(), classes, seen)
                : find(member.getValue(), path + "/" + member.getKey(), classes, seen);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }
}
