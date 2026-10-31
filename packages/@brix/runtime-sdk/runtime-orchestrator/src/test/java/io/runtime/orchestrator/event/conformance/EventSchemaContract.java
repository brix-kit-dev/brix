/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.RecordComponent;
import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import io.runtime.manifest.loader.PluginManifestLoader;
import io.runtime.manifest.model.PluginManifest;

/**
 * Checks a publisher's event schemas against its manifest and its payload records (Kafka design D24).
 * <p>Every {@code schemaRef} the manifest declares under {@code events.publishes} resolves to a schema file in
 * the publisher's artifact, at {@code META-INF/brix/schemas/<owner>/<name>/<version>.json}. The schema names
 * exactly the payload record's fields with matching JSON types and allows no other field, and a sample payload,
 * written the way the outbox writes it, validates against it. These files are the Contract Schema Registry:
 * versioned build assets, not a network service.</p>
 * <p>Each published event also has its contract file, {@code META-INF/brix/events/<eventType>/<version>.json}, naming
 * the same schema, so that the contract tools check the schema against its earlier minor versions and pin it in the
 * build of the artifact that holds it (standard-link plan L5.1).</p>
 */
public final class EventSchemaContract {

    private static final String SCHEMA_PREFIX = "brix://schemas/";
    private static final ObjectMapper JSON = JsonMapper.builder()
        .configureForJackson2()
        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build();

    private EventSchemaContract() {
    }

    /**
     * Verifies every event the plugin publishes.
     *
     * @param anchor a class of the publisher's artifact, to find its manifest and schema files
     * @param samples a sample payload for each published event type, of the record the publisher writes
     */
    public static void verify(Class<?> anchor, Map<String, ?> samples) {
        PluginManifest manifest = new PluginManifestLoader().loadActiveFromClasspath(anchor);
        List<PluginManifest.EventPublish> publishes = manifest.getEvents().getPublishes();
        assertEquals(publishes.stream().map(PluginManifest.EventPublish::getEventType).collect(Collectors.toSet()),
            samples.keySet(), "a sample payload for each event type " + manifest.pluginId() + " publishes");
        for (PluginManifest.EventPublish publish : publishes) {
            verify(anchor.getClassLoader(), publish, samples.get(publish.getEventType()));
        }
    }

    private static void verify(ClassLoader classes, PluginManifest.EventPublish publish, Object sample) {
        String eventType = publish.getEventType();
        String schemaRef = publish.getSchemaRef();
        assertTrue(schemaRef != null && schemaRef.startsWith(SCHEMA_PREFIX), eventType + " declares a brix schemaRef");
        assertTrue(schemaRef.endsWith("/" + publish.getVersion()), eventType + " refers to the schema of version "
            + publish.getVersion());
        String resource = "META-INF/brix/schemas/" + schemaRef.substring(SCHEMA_PREFIX.length()) + ".json";
        JsonNode schema = read(classes, resource);
        assertEquals(schemaRef, schema.path("$id").asString(), resource + " names itself by its schemaRef");
        assertEquals("object", schema.path("type").asString(), resource + " describes an object");
        assertFalse(schema.path("additionalProperties").asBoolean(true), resource + " allows no other field");

        Class<?> payload = sample.getClass();
        assertTrue(payload.isRecord(), eventType + " has a record payload");
        Set<String> required = names(schema.path("required"));
        JsonNode properties = schema.path("properties");
        Map<String, Class<?>> components = Arrays.stream(payload.getRecordComponents())
            .collect(Collectors.toMap(RecordComponent::getName, RecordComponent::getType, (a, b) -> a,
                java.util.LinkedHashMap::new));
        assertEquals(components.keySet(), new LinkedHashSet<>(properties.propertyNames()),
            resource + " names exactly the fields of " + payload.getSimpleName());
        components.forEach((name, type) -> {
            Set<String> types = names(properties.path(name).path("type"));
            assertTrue(types.contains(jsonType(type)), resource + " gives " + name + " the JSON type " + jsonType(type));
            if (type.isPrimitive()) {
                assertTrue(required.contains(name), resource + " requires " + name + ", which is never null");
                assertFalse(types.contains("null"), resource + " does not allow null for " + name);
            }
        });
        assertTrue(properties.propertyNames().containsAll(required), resource + " requires only fields it names");

        String contract = "META-INF/brix/events/" + eventType + "/" + publish.getVersion() + ".json";
        JsonNode declared = read(classes, contract);
        assertEquals(eventType, declared.path("eventType").asString(), contract + " names " + eventType);
        assertEquals(publish.getVersion(), declared.path("version").asString(), contract + " names its version");
        assertEquals(schemaRef, declared.path("payload").asString(), contract + " names the payload schema " + schemaRef);

        List<com.networknt.schema.Error> errors = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(schema)
            .validate(JSON.valueToTree(sample));
        assertTrue(errors.isEmpty(), "the sample " + eventType + " payload validates against " + resource + ": " + errors);
    }

    private static JsonNode read(ClassLoader classes, String resource) {
        try (InputStream in = classes.getResourceAsStream(resource)) {
            assertNotNull(in, "the publisher's artifact holds " + resource);
            return JSON.readTree(in);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static Set<String> names(JsonNode node) {
        if (node.isString()) {
            return Set.of(node.asString());
        }
        return node.valueStream().map(JsonNode::asString).collect(Collectors.toSet());
    }

    private static String jsonType(Class<?> type) {
        if (type == String.class || type.isEnum() || Temporal.class.isAssignableFrom(type) || type == UUID.class) {
            return "string";
        }
        if (type == long.class || type == Long.class || type == int.class || type == Integer.class
                || type == short.class || type == Short.class || type == java.math.BigInteger.class) {
            return "integer";
        }
        if (type == double.class || type == Double.class || type == float.class || type == Float.class
                || type == java.math.BigDecimal.class) {
            return "number";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        if (type.isArray() || Collection.class.isAssignableFrom(type)) {
            return "array";
        }
        return "object";
    }
}
