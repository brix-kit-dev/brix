/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.contract.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.runtime.sdk.query.ContractSchema;
import io.runtime.sdk.query.QueryType;

/**
 * Query design D38 and §5.6 (root BRX-RS-A-22, MANIFEST-027, CAP-019 to CAP-021): contract types generated from the
 * schemas, checked on construction, and compatible across the minor versions of a major.
 */
class QueryContractGeneratorTest {

    private static final String PACKAGE = "io.test.shop.api";
    private static final String NOTE = ",\n  \"note\": {\"type\": \"string\", \"maxLength\": 200, "
        + "\"x-brix-since\": \"1.1.0\", \"description\": \"A note\"}";

    @TempDir
    Path module;

    private Path resources;

    @BeforeEach
    void setUp() throws IOException {
        resources = module.resolve("src/main/resources");
        schema("find-order-request", "1.0.0", """
            "title": "Find an order",
            "type": "object", "additionalProperties": false, "required": ["orderId"],
            "properties": {
              "orderId": {"type": "string", "minLength": 1, "maxLength": 32, "pattern": "[A-Z0-9-]+"}
            }""");
        schema("find-order-request", "1.1.0", """
            "title": "Find an order",
            "type": "object", "additionalProperties": false, "required": ["orderId"],
            "properties": {
              "orderId": {"type": "string", "minLength": 1, "maxLength": 32, "pattern": "[A-Z0-9-]+"}
            }""");
        schema("order", "1.0.0", order(""));
        schema("order", "1.1.0", order(NOTE));
        query("1.0.0");
        query("1.1.0");
    }

    @Test
    void theSchemasGenerateRecordsTheQueryConstantsAndAnApiListing() {
        QueryContractGenerator.Generated generated = QueryContractGenerator.generate(resources, PACKAGE);

        assertEquals(List.of("FindOrderRequest", "Order", "TestShopQueries"), List.copyOf(generated.sources().keySet()));
        String order = generated.sources().get("Order");
        assertTrue(order.contains("@ContractSchema(\"brix://schemas/test-shop/order/1.1.0\")"), order);
        assertTrue(order.contains("Creates the record from the properties of version 1.0.0"), order);
        assertTrue(generated.sources().get("TestShopQueries").contains("QueryType.optional(\"test-shop.find-order\", "
            + "\"1.1.0\", FindOrderRequest.class, Order.class)"));
        assertTrue(generated.api().contains("constructor Order(String, long, Status, List<String>, Instant)"),
            generated.api().toString());
        assertTrue(generated.api().contains("constructor Order(String, long, Status, List<String>, Instant, String)"));
        assertTrue(generated.api().contains(
            "query TestShopQueries.FIND_ORDER = test-shop.find-order major 1 optional FindOrderRequest -> Order"));
    }

    @Test
    void theGeneratedTypesCompileAndRefuseValuesOutsideTheirSchema() throws Exception {
        try (URLClassLoader loader = compile(QueryContractGenerator.generate(resources, PACKAGE))) {
            Class<?> order = loader.loadClass(PACKAGE + ".Order");
            Class<?> status = loader.loadClass(PACKAGE + ".Order$Status");
            Object builder = order.getMethod("builder").invoke(null);
            call(builder, "id", String.class, "O-1");
            call(builder, "quantity", Long.class, 2L);
            call(builder, "status", status, status.getEnumConstants()[0]);
            call(builder, "tags", List.class, new ArrayList<>(List.of("a", "b")));
            Object built = builder.getClass().getMethod("build").invoke(builder);

            assertEquals("O-1", order.getMethod("id").invoke(built));
            assertThrows(UnsupportedOperationException.class,
                () -> ((List<?>) order.getMethod("tags").invoke(built)).clear(), "lists are copied");
            assertEquals("brix://schemas/test-shop/order/1.1.0", order.getAnnotation(ContractSchema.class).value());

            Object fromOldMinor = order.getConstructor(String.class, long.class, status, List.class,
                java.time.Instant.class).newInstance("O-2", 1L, status.getEnumConstants()[1], List.of(), null);
            assertEquals(null, order.getMethod("note").invoke(fromOldMinor), "code of 1.0.0 still runs on 1.1.0");

            assertRefused(() -> order.getConstructor(String.class, long.class, status, List.class,
                java.time.Instant.class).newInstance("O-3", 0L, status.getEnumConstants()[0], List.of(), null));
            assertRefused(() -> order.getConstructor(String.class, long.class, status, List.class,
                java.time.Instant.class).newInstance("O-4", 1L, status.getEnumConstants()[0],
                    List.of("1", "2", "3", "4", "5", "6"), null));
            Class<?> request = loader.loadClass(PACKAGE + ".FindOrderRequest");
            assertRefused(() -> request.getConstructor(String.class).newInstance("lower-case"));
            assertRefused(() -> request.getConstructor(String.class).newInstance((Object) null));

            QueryType<?, ?> type = (QueryType<?, ?>) loader.loadClass(PACKAGE + ".TestShopQueries")
                .getField("FIND_ORDER").get(null);
            assertTrue(type.optionalResult());
            assertEquals(order, type.resultType());
            assertEquals(request, type.requestType());
        }
    }

    @Test
    void aSecondMajorGetsTypesAndAConstantOfItsOwn() throws IOException {
        schema("find-order-request", "2.0.0", """
            "type": "object", "additionalProperties": false, "required": ["orderId", "region"],
            "properties": {"orderId": {"type": "string"}, "region": {"type": "string"}}""");
        schema("order", "2.0.0", """
            "type": "object", "additionalProperties": false, "required": ["id"],
            "properties": {"id": {"type": "string"}}""");
        query("2.0.0");

        QueryContractGenerator.Generated generated = QueryContractGenerator.generate(resources, PACKAGE);

        assertTrue(generated.sources().containsKey("OrderV2"));
        assertTrue(generated.sources().containsKey("FindOrderRequestV2"));
        assertTrue(generated.sources().get("TestShopQueries").contains("FIND_ORDER_V2"));
        assertTrue(generated.sources().containsKey("Order"), "the first major stays during its deprecation window");
    }

    @Test
    void aMinorVersionThatChangesOrDropsAPropertyIsRefused() throws IOException {
        schema("order", "1.2.0", """
            "type": "object", "additionalProperties": false, "required": ["id", "quantity", "status"],
            "properties": {
              "id": {"type": "string"}, "quantity": {"type": "string"},
              "status": {"type": "string", "enum": ["PLACED", "SHIPPED"]},
              "tags": {"type": "array", "maxItems": 5, "items": {"type": "string"}},
              "placedAt": {"type": "string", "format": "date-time"},
              "note": {"type": "string", "maxLength": 200, "x-brix-since": "1.1.0"}
            }""");
        schema("find-order-request", "1.2.0", """
            "type": "object", "additionalProperties": false, "required": ["orderId"],
            "properties": {"orderId": {"type": "string", "minLength": 1, "maxLength": 32, "pattern": "[A-Z0-9-]+"}}""");
        query("1.2.0");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
            () -> QueryContractGenerator.generate(resources, PACKAGE));
        assertTrue(refused.getMessage().contains("needs a new major version"), refused.getMessage());
    }

    @Test
    void anAddedPropertyMustBeOptionalAndMarkedWithItsVersion() throws IOException {
        schema("order", "1.2.0", order(NOTE + ",\n  \"carrier\": {\"type\": \"string\"}").replace("\"required\": [\"id\", \"quantity\", \"status\"]",
            "\"required\": [\"id\", \"quantity\", \"status\", \"carrier\"]"));
        query("1.2.0");
        schema("find-order-request", "1.2.0", """
            "type": "object", "additionalProperties": false, "required": ["orderId"],
            "properties": {"orderId": {"type": "string", "minLength": 1, "maxLength": 32, "pattern": "[A-Z0-9-]+"}}""");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
            () -> QueryContractGenerator.generate(resources, PACKAGE));
        assertTrue(refused.getMessage().contains("must be optional and marked x-brix-since"), refused.getMessage());
    }

    @Test
    void anUnboundedArrayOrAnUnsupportedKeywordIsRefused() throws IOException {
        schema("order", "1.1.0", order("").replace("\"maxItems\": 5, ", ""));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> QueryContractGenerator.generate(resources, PACKAGE))
            .getMessage().contains("maxItems"));

        schema("order", "1.1.0", order(",\n  \"note\": {\"oneOf\": [{\"type\": \"string\"}, {\"type\": \"null\"}], "
            + "\"x-brix-since\": \"1.1.0\"}"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> QueryContractGenerator.generate(resources, PACKAGE))
            .getMessage().contains("oneOf"));
    }

    @Test
    void theLatestQueryVersionMustUseTheLatestSchemas() throws IOException {
        Files.delete(resources.resolve("META-INF/brix/queries/test-shop.find-order/1.1.0.json"));

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
            () -> QueryContractGenerator.generate(resources, PACKAGE));
        assertTrue(refused.getMessage().contains("add the query contract of that version"), refused.getMessage());
    }

    @Test
    void verificationFailsOnStaleSourcesAndOnApiAMinorWouldLose() throws IOException {
        System.setProperty(ContractSources.WRITE, "true");
        try {
            ContractSources.verify(module, PACKAGE);
        } finally {
            System.clearProperty(ContractSources.WRITE);
        }
        ContractSources.verify(module, PACKAGE);

        Path order = module.resolve("src/main/java/io/test/shop/api/Order.java");
        Files.writeString(order, Files.readString(order) + "\n// edited by hand\n");
        AssertionError stale = assertThrows(AssertionError.class, () -> ContractSources.verify(module, PACKAGE));
        assertTrue(stale.getMessage().contains("Order.java"), stale.getMessage());

        Files.delete(resources.resolve("META-INF/brix/queries/test-shop.find-order/1.1.0.json"));
        Files.delete(resources.resolve("META-INF/brix/schemas/test-shop/order/1.1.0.json"));
        Files.delete(resources.resolve("META-INF/brix/schemas/test-shop/find-order-request/1.1.0.json"));
        AssertionError lost = assertThrows(AssertionError.class, () -> ContractSources.verify(module, PACKAGE));
        assertTrue(lost.getMessage().contains("would lose API"), lost.getMessage());
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static String order(String extra) {
        return """
            "title": "An order",
            "type": "object", "additionalProperties": false, "required": ["id", "quantity", "status"],
            "properties": {
              "id": {"type": "string", "x-brix-classification": "internal-identifier"},
              "quantity": {"type": "integer", "minimum": 1},
              "status": {"type": "string", "enum": ["PLACED", "SHIPPED"]},
              "tags": {"type": "array", "maxItems": 5, "items": {"type": "string"}},
              "placedAt": {"type": "string", "format": "date-time"}""" + extra + "\n}";
    }

    @Test
    void aCommandContractGeneratesItsRequestAndACommandTypeConstant() throws Exception {
        // Standard-link plan L4.1: commands get generated types too (root A-22).
        schema("cancel-order", "1.0.0", """
            "title": "Cancel an order",
            "type": "object", "additionalProperties": false, "required": ["orderId", "reason"],
            "properties": {
              "orderId": {"type": "string", "minLength": 1, "maxLength": 32},
              "reason": {"type": "string", "enum": ["REJECTED_BY_SHIPPING"]}
            }""");
        command("cancel-order", "1.0.0");

        QueryContractGenerator.Generated generated = QueryContractGenerator.generate(resources, PACKAGE);

        assertTrue(generated.sources().containsKey("CancelOrder"), generated.sources().keySet().toString());
        String constants = generated.sources().get("TestShopCommands");
        assertTrue(constants.contains("public static final CommandType<CancelOrder> CANCEL_ORDER ="), constants);
        assertTrue(constants.contains("CommandType.of(\"test-shop.cancel-order\", \"1.0.0\", CancelOrder.class)"),
            constants);
        assertTrue(generated.api().contains(
            "command TestShopCommands.CANCEL_ORDER = test-shop.cancel-order major 1 CancelOrder"),
            generated.api().toString());
        try (URLClassLoader loader = compile(generated)) {
            assertEquals("test-shop.cancel-order", loader.loadClass(PACKAGE + ".TestShopCommands")
                .getField("CANCEL_ORDER").get(null).toString().replace("@1.0.0", ""));
        }
    }

    @Test
    void aCommandContractMustLiveAtItsTypeAndVersion() throws IOException {
        schema("cancel-order", "1.0.0", """
            "type": "object", "additionalProperties": false, "required": ["orderId"],
            "properties": {"orderId": {"type": "string", "maxLength": 32}}""");
        Path misplaced = resources.resolve("META-INF/brix/commands/test-shop.cancel-order/2.0.0.json");
        Files.createDirectories(misplaced.getParent());
        Files.writeString(misplaced, """
            {"commandType": "test-shop.cancel-order", "version": "1.0.0",
             "request": "brix://schemas/test-shop/cancel-order/1.0.0"}""");

        IllegalArgumentException refused = org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class, () -> QueryContractGenerator.generate(resources, PACKAGE));
        assertTrue(refused.getMessage().contains("<commandType>/<version>.json"), refused.getMessage());
    }

    @Test
    void anEventContractChecksAndLocksItsPayloadWithoutGeneratingIt() throws IOException {
        // Standard-link plan L5.1: events go through the same checks; their payload records stay hand-written.
        schema("order-shipped", "1.0.0", """
            "title": "An order was shipped",
            "type": "object", "additionalProperties": false, "required": ["orderId", "parcels"],
            "properties": {
              "orderId": {"type": "string", "maxLength": 32},
              "parcels": {"type": "array", "items": {"type": "integer"}},
              "carrier": {"type": ["string", "null"], "maxLength": 32}
            }""");
        event("OrderShipped", "order-shipped", "1.0.0");

        QueryContractGenerator.Generated generated = QueryContractGenerator.generate(resources, PACKAGE);

        assertFalse(generated.sources().containsKey("OrderShipped"), generated.sources().keySet().toString());
        assertTrue(generated.api().contains("event OrderShipped major 1 payload test-shop/order-shipped"),
            generated.api().toString());
        assertTrue(generated.api().stream().anyMatch(line -> line.matches(
            "schema test-shop/order-shipped 1\\.0\\.0 sha256:[0-9a-f]{64}")), generated.api().toString());
    }

    @Test
    void anEventPayloadIsHeldToTheSubsetAndToItsEarlierMinorVersions() throws IOException {
        schema("order-shipped", "1.0.0", """
            "type": "object", "additionalProperties": false, "required": ["orderId"],
            "properties": {"orderId": {"type": "string", "maxLength": 32}, "carrier": {"type": "string"}}""");
        schema("order-shipped", "1.1.0", """
            "type": "object", "additionalProperties": false, "required": ["orderId"],
            "properties": {"orderId": {"type": "string", "maxLength": 32}}""");
        event("OrderShipped", "order-shipped", "1.0.0");
        event("OrderShipped", "order-shipped", "1.1.0");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> QueryContractGenerator.generate(resources, PACKAGE))
            .getMessage().contains("removes properties"));

        schema("order-shipped", "1.1.0", """
            "type": "object", "additionalProperties": false, "required": ["orderId"],
            "properties": {"orderId": {"type": "string", "maxLength": 32},
              "carrier": {"oneOf": [{"type": "string"}, {"type": "null"}]}}""");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> QueryContractGenerator.generate(resources, PACKAGE))
            .getMessage().contains("oneOf"));
    }

    @Test
    void aMinorVersionMayAddEnumValuesAfterTheEarlierOnes() throws Exception {
        // Root BRX-RS-CAP-021: a published enum is open; a later minor adds values, callers switch with a default.
        schema("order", "1.2.0", order(NOTE).replace("[\"PLACED\", \"SHIPPED\"]", "[\"PLACED\", \"SHIPPED\", \"CANCELLED\"]"));
        schema("find-order-request", "1.2.0", """
            "type": "object", "additionalProperties": false, "required": ["orderId"],
            "properties": {"orderId": {"type": "string", "minLength": 1, "maxLength": 32, "pattern": "[A-Z0-9-]+"}}""");
        query("1.2.0");

        QueryContractGenerator.Generated generated = QueryContractGenerator.generate(resources, PACKAGE);

        assertTrue(generated.api().contains("enum Order.Status.CANCELLED"), generated.api().toString());
        assertTrue(generated.sources().get("Order").contains("a switch over them"), generated.sources().get("Order"));
        try (URLClassLoader loader = compile(generated)) {
            Object[] values = loader.loadClass(PACKAGE + ".Order$Status").getEnumConstants();
            assertEquals("[PLACED, SHIPPED, CANCELLED]", java.util.Arrays.toString(values));
        }
    }

    @Test
    void aMinorVersionThatReordersOrDropsEnumValuesIsRefused() throws IOException {
        schema("order", "1.2.0", order(NOTE).replace("[\"PLACED\", \"SHIPPED\"]", "[\"SHIPPED\", \"PLACED\"]"));
        schema("find-order-request", "1.2.0", """
            "type": "object", "additionalProperties": false, "required": ["orderId"],
            "properties": {"orderId": {"type": "string", "minLength": 1, "maxLength": 32, "pattern": "[A-Z0-9-]+"}}""");
        query("1.2.0");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
            () -> QueryContractGenerator.generate(resources, PACKAGE));
        assertTrue(refused.getMessage().contains("changes property status"), refused.getMessage());
    }

    @Test
    void aTypeListWithNullIsAnOptionalProperty() throws IOException {
        schema("order", "1.1.0", order(",\n  \"note\": {\"type\": [\"string\", \"null\"], \"maxLength\": 200, "
            + "\"x-brix-since\": \"1.1.0\"}"));
        assertTrue(QueryContractGenerator.generate(resources, PACKAGE).api().contains(
            "accessor Order.note(): String optional"));

        schema("order", "1.1.0", order(",\n  \"note\": {\"type\": [\"string\", \"integer\"], "
            + "\"x-brix-since\": \"1.1.0\"}"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> QueryContractGenerator.generate(resources, PACKAGE))
            .getMessage().contains("[\"<type>\", \"null\"]"));

        schema("order", "1.1.0", order(NOTE));
        schema("order", "1.0.0", order("").replace("\"quantity\": {\"type\": \"integer\"",
            "\"quantity\": {\"type\": [\"integer\", \"null\"]"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> QueryContractGenerator.generate(resources, PACKAGE))
            .getMessage().contains("only an optional property may be null"));
    }

    @Test
    void everyPublishedSchemaVersionIsPinnedToItsContent() throws IOException {
        // Standard-link plan L5.2: reformatting keeps the hash, any other edit of a published file fails verification.
        System.setProperty(ContractSources.WRITE, "true");
        try {
            ContractSources.verify(module, PACKAGE);
        } finally {
            System.clearProperty(ContractSources.WRITE);
        }
        List<String> locks = QueryContractGenerator.generate(resources, PACKAGE).api().stream()
            .filter(line -> line.startsWith("schema ")).toList();
        assertEquals(List.of("test-shop/find-order-request 1.0.0", "test-shop/find-order-request 1.1.0",
            "test-shop/order 1.0.0", "test-shop/order 1.1.0"),
            locks.stream().map(line -> line.substring("schema ".length(), line.indexOf(" sha256:"))).toList());

        Path published = resources.resolve("META-INF/brix/schemas/test-shop/order/1.0.0.json");
        Files.writeString(published, Files.readString(published).replace("\n", "\n    ").replace("\": ", "\":"));
        ContractSources.verify(module, PACKAGE);

        Files.writeString(published, Files.readString(published).replace("An order", "An order, as placed"));
        AssertionError changed = assertThrows(AssertionError.class, () -> ContractSources.verify(module, PACKAGE));
        assertTrue(changed.getMessage().contains("Published schema versions"), changed.getMessage());
        assertTrue(changed.getMessage().contains("schema test-shop/order 1.0.0 sha256:"), changed.getMessage());
    }

    @Test
    void aSchemaNoQueryCommandOrEventUsesIsPinnedTooAsAnEndpointsIs() throws IOException {
        // Queue item 3 (D114): an endpoint's request or response schema is published as much as a query's. It may use
        // what the generator's subset leaves out, because no type is generated from it.
        schema("place-order-request", "1.0.0", """
            "title": "Place an order",
            "type": "object", "additionalProperties": false, "required": ["sku"],
            "properties": {
              "sku": {"type": "string", "minLength": 1},
              "contact": {"oneOf": [{"type": "string", "format": "email"}, {"type": "null"}]}
            }""");
        System.setProperty(ContractSources.WRITE, "true");
        try {
            ContractSources.verify(module, PACKAGE);
        } finally {
            System.clearProperty(ContractSources.WRITE);
        }
        List<String> api = QueryContractGenerator.generate(resources, PACKAGE).api();
        assertTrue(api.stream().anyMatch(line -> line.startsWith("schema test-shop/place-order-request 1.0.0 sha256:")),
            api.toString());
        assertFalse(QueryContractGenerator.generate(resources, PACKAGE).sources().containsKey("PlaceOrderRequest"));

        Path published = resources.resolve("META-INF/brix/schemas/test-shop/place-order-request/1.0.0.json");
        Files.writeString(published, Files.readString(published).replace("\"minLength\": 1}", "\"minLength\": 2}"));
        AssertionError changed = assertThrows(AssertionError.class, () -> ContractSources.verify(module, PACKAGE));
        assertTrue(changed.getMessage().contains("schema test-shop/place-order-request 1.0.0 sha256:"),
            changed.getMessage());
    }

    @Test
    void aModuleWithEndpointSchemasOnlyHasAListingOfLocksAndNoSources(@TempDir Path plugin) throws IOException {
        // Queue item 3 (D114): a plugin or operational module ships the schemas of its endpoints and generates no type.
        Path file = plugin.resolve("src/main/resources/META-INF/brix/schemas/test-shop/placed-order/1.0.0.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\n\"$schema\": \"https://json-schema.org/draft/2020-12/schema\",\n"
            + "\"$id\": \"brix://schemas/test-shop/placed-order/1.0.0\",\n\"type\": \"object\"\n}\n");
        System.setProperty(ContractSources.WRITE, "true");
        try {
            ContractSources.verify(plugin, "io.test.shop.server");
        } finally {
            System.clearProperty(ContractSources.WRITE);
        }
        List<String> listing = Files.readAllLines(plugin.resolve(ContractSources.API_LISTING));
        assertEquals(1, listing.size(), listing.toString());
        assertTrue(listing.get(0).startsWith("schema test-shop/placed-order 1.0.0 sha256:"), listing.toString());
        Path sources = plugin.resolve("src/main/java/io/test/shop/server");
        try (var generatedFiles = Files.exists(sources) ? Files.list(sources) : java.util.stream.Stream.<Path>empty()) {
            assertEquals(0, generatedFiles.count(), "no source is generated");
        }
        ContractSources.verify(plugin, "io.test.shop.server");

        Files.delete(file);
        AssertionError gone = assertThrows(AssertionError.class, () -> ContractSources.verify(plugin, "io.test.shop.server"));
        assertTrue(gone.getMessage().contains("changed or disappeared"), gone.getMessage());
    }

    private void event(String eventType, String name, String version) throws IOException {
        Path file = resources.resolve("META-INF/brix/events/" + eventType + "/" + version + ".json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"eventType\": \"" + eventType + "\", \"version\": \"" + version
            + "\", \"payload\": \"brix://schemas/test-shop/" + name + "/" + version + "\"}");
    }

    private void command(String name, String version) throws IOException {
        Path file = resources.resolve("META-INF/brix/commands/test-shop." + name + "/" + version + ".json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"commandType\": \"test-shop." + name + "\", \"version\": \"" + version
            + "\", \"request\": \"brix://schemas/test-shop/" + name + "/" + version + "\"}");
    }

    private void schema(String name, String version, String body) throws IOException {
        Path file = resources.resolve("META-INF/brix/schemas/test-shop/" + name + "/" + version + ".json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\n\"$schema\": \"https://json-schema.org/draft/2020-12/schema\",\n"
            + "\"$id\": \"brix://schemas/test-shop/" + name + "/" + version + "\",\n" + body + "\n}\n");
    }

    private void query(String version) throws IOException {
        Path file = resources.resolve("META-INF/brix/queries/test-shop.find-order/" + version + ".json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"queryType\": \"test-shop.find-order\", \"version\": \"" + version + "\", "
            + "\"request\": \"brix://schemas/test-shop/find-order-request/" + version + "\", "
            + "\"response\": \"brix://schemas/test-shop/order/" + version + "\", \"optional\": true, "
            + "\"description\": \"an order by its id\"}\n");
    }

    private URLClassLoader compile(QueryContractGenerator.Generated generated) throws IOException {
        Path sources = module.resolve("generated/" + PACKAGE.replace('.', '/'));
        Files.createDirectories(sources);
        List<String> arguments = new ArrayList<>(List.of("-d", module.resolve("classes").toString(), "-classpath",
            System.getProperty("java.class.path"), "--release", "17"));
        for (Map.Entry<String, String> source : generated.sources().entrySet()) {
            Path file = sources.resolve(source.getKey() + ".java");
            Files.writeString(file, source.getValue());
            arguments.add(file.toString());
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertEquals(0, compiler.run(null, null, null, arguments.toArray(String[]::new)), "the sources compile");
        return new URLClassLoader(new URL[] {module.resolve("classes").toUri().toURL()},
            QueryContractGeneratorTest.class.getClassLoader());
    }

    private static void call(Object builder, String name, Class<?> type, Object value) throws Exception {
        Method setter = builder.getClass().getMethod(name, type);
        setter.invoke(builder, value);
    }

    private static void assertRefused(ThrowingCall call) {
        InvocationTargetException failure = assertThrows(InvocationTargetException.class, call::run);
        assertTrue(failure.getCause() instanceof IllegalArgumentException
            || failure.getCause() instanceof NullPointerException, String.valueOf(failure.getCause()));
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }

}
