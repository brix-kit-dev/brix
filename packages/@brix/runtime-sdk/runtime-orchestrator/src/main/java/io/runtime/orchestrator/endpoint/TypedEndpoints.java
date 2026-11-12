/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import io.runtime.sdk.plugin.EndpointResponse;
import io.runtime.sdk.plugin.TypedEndpointHandler;

/**
 * The runtime's half of a {@link TypedEndpointHandler} (root BRX-RS-MANIFEST-022, §5.4; standard-link plan L7.2,
 * L7.3, D58, D59).
 *
 * <ul>
 *   <li>The request: the body of a {@code POST}, {@code PUT} or {@code PATCH}, or the query parameters of any other
 *   method, together with the path variables, as one JSON object. Parameters are typed by the request schema's
 *   properties: integers, numbers, booleans and arrays of them. Its numbers are the ones the client wrote: a decimal
 *   keeps every digit, and none is longer written out than the host's parser takes one (queue item 7, D123, D124).
 *   The object is checked against the request schema, then read into the handler's {@code B}; a class names {@code B}
 *   in its {@code implements} clause, a lambda gets the JSON object.</li>
 *   <li>The response: the handler's {@code R}, checked against the response schema, with the declared status; a
 *   {@code 204} has no body.</li>
 * </ul>
 */
final class TypedEndpoints {

    private static final Logger log = LoggerFactory.getLogger(TypedEndpoints.class);
    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");

    private final ObjectMapper json;
    private final EndpointSchemas schemas;
    private final int maxDigits;
    private final Map<Class<?>, Type> inputs = new ConcurrentHashMap<>();

    TypedEndpoints(ObjectMapper json, ClassLoader classes) {
        this.json = Objects.requireNonNull(json, "json must not be null");
        this.schemas = new EndpointSchemas(classes, json);
        this.maxDigits = EndpointNumbers.maxDigits(json);
    }

    /**
     * The request of a typed handler.
     *
     * @param value what the handler gets, its {@code B}
     * @param tree the request the schema checked, which a replayed endpoint's fingerprint is taken from
     */
    record Input(Object value, JsonNode tree) {
    }

    /**
     * Builds and checks the request of a typed handler.
     *
     * @param route the route
     * @param handler its handler
     * @param method the request's method
     * @param body the request body, or {@code null}
     * @param exactBody reads the body's JSON text with every decimal digit; {@code null} when the caller has no text
     * @param query the query parameters
     * @param pathVariables the path variables
     * @return the request
     * @throws EndpointRequestInvalidException when it breaks the request schema or does not read as {@code B}
     */
    Input input(EndpointRoute route, TypedEndpointHandler<?, ?> handler, String method, Object body,
            Supplier<JsonNode> exactBody, Map<String, List<String>> query, Map<String, String> pathVariables) {
        Optional<EndpointSchemas.Compiled> schema = schemas.schema(route.contract().requestSchemaRef());
        ObjectNode request = json.createObjectNode();
        if (BODY_METHODS.contains(method)) {
            if (body != null) {
                // Standard-link plan queue item 7, D123: the numbers the client wrote, not their nearest doubles.
                JsonNode tree = exactBody != null ? exactBody.get() : json.valueToTree(body);
                if (!tree.isObject()) {
                    throw invalid("$", "type");
                }
                request.setAll((ObjectNode) tree);
            }
        } else if (query != null) {
            query.forEach((name, values) -> request.set(name, parameter(schema, name, values)));
        }
        pathVariables.forEach((name, value) -> request.set(name, parameter(schema, name, List.of(value))));
        // D124: before anything works on its numbers, the schema check's multipleOf and the fingerprint among them.
        List<String> tooLong = EndpointNumbers.tooLong(request, maxDigits);
        if (!tooLong.isEmpty()) {
            throw new EndpointRequestInvalidException(tooLong.stream()
                .map(path -> Map.of("path", path, "rule", "type"))
                .toList());
        }
        if (schema.isPresent()) {
            List<EndpointSchemas.Violation> violations = EndpointSchemas.violations(schema.get(), request);
            if (!violations.isEmpty()) {
                throw new EndpointRequestInvalidException(violations.stream()
                    .map(violation -> Map.of("path", violation.path(), "rule", violation.rule()))
                    .toList());
            }
        }
        Type type = inputs.computeIfAbsent(handler.getClass(), TypedEndpoints::inputType);
        if (type == JsonNode.class || type == ObjectNode.class || type == Object.class) {
            return new Input(request, request);
        }
        try {
            return new Input(json.readerFor(json.constructType(type))
                .with(EnumFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
                .readValue(request), request);
        } catch (JacksonException | IllegalArgumentException unreadable) {
            throw invalid("$", "type");
        }
    }

    /**
     * Waits for a typed handler's response, no longer than the deadline.
     *
     * @param route the route
     * @param stage what the handler returned
     * @param deadline the invocation's deadline
     * @param cancel sets the invocation's cancellation signal
     * @return the response
     * @throws EndpointDeadlineExceededException when the deadline passes first
     */
    static Object await(EndpointRoute route, CompletionStage<?> stage, Instant deadline, Runnable cancel) {
        if (stage == null) {
            throw new IllegalStateException("Endpoint handler '" + route.endpointId() + "' returned no CompletionStage");
        }
        long remaining = Math.max(1, Duration.between(Instant.now(), deadline).toMillis());
        try {
            return stage.toCompletableFuture().get(remaining, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | InterruptedException late) {
            if (late instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            cancel.run();
            stage.toCompletableFuture().cancel(true);
            throw new EndpointDeadlineExceededException(route.endpointId());
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new CompletionException(cause);
        }
    }

    /**
     * Checks a typed handler's response and answers with the declared status.
     *
     * @param route the route
     * @param result the handler's {@code R}
     * @return the response
     * @throws EndpointResponseInvalidException when it breaks the response schema
     */
    EndpointResponse output(EndpointRoute route, Object result) {
        EndpointContract contract = route.contract();
        if (contract.status() == 204) {
            return new EndpointResponse(204, null);
        }
        JsonNode tree = json.valueToTree(result);
        Optional<EndpointSchemas.Compiled> schema = schemas.schema(contract.responseSchemaRef());
        if (schema.isPresent()) {
            List<EndpointSchemas.Violation> violations = EndpointSchemas.violations(schema.get(), tree);
            if (!violations.isEmpty()) {
                log.error("event=endpoint.response_invalid plugin={} endpoint={} violations={}", route.pluginId(),
                    route.endpointId(), violations);
                throw new EndpointResponseInvalidException(route.endpointId());
            }
        }
        return new EndpointResponse(contract.status(), tree);
    }

    private JsonNode parameter(Optional<EndpointSchemas.Compiled> schema, String name, List<String> values) {
        JsonNode property = schema.map(compiled -> compiled.node().path("properties").path(name))
            .orElse(json.missingNode());
        String type = typeOf(property);
        if ("array".equals(type)) {
            ArrayNode array = json.createArrayNode();
            String items = typeOf(property.path("items"));
            values.forEach(value -> array.add(scalar(items, value)));
            return array;
        }
        if (values.size() != 1) {
            ArrayNode array = json.createArrayNode();
            values.forEach(array::add);
            return array;
        }
        return scalar(type, values.get(0));
    }

    private JsonNode scalar(String type, String value) {
        try {
            return switch (type) {
                case "integer" -> json.getNodeFactory().numberNode(Long.parseLong(value.trim()));
                case "number" -> json.getNodeFactory().numberNode(new BigDecimal(value.trim()));
                case "boolean" -> "true".equals(value) || "false".equals(value)
                    ? json.getNodeFactory().booleanNode(Boolean.parseBoolean(value))
                    : json.getNodeFactory().stringNode(value);
                default -> json.getNodeFactory().stringNode(value);
            };
        } catch (NumberFormatException notANumber) {
            // The schema check reports it as a type the value does not have.
            return json.getNodeFactory().stringNode(value);
        }
    }

    private static String typeOf(JsonNode property) {
        JsonNode type = property.path("type");
        if (type.isArray()) {
            for (JsonNode entry : type) {
                if (!"null".equals(entry.asString())) {
                    return entry.asString();
                }
            }
        }
        return type.asString("string");
    }

    private static EndpointRequestInvalidException invalid(String path, String rule) {
        return new EndpointRequestInvalidException(List.of(Map.of("path", path, "rule", rule)));
    }

    /** The {@code B} a handler class names in its {@code implements} clause; a JSON object when it names none. */
    static Type inputType(Class<?> handler) {
        for (Class<?> type = handler; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Type implemented : type.getGenericInterfaces()) {
                if (implemented instanceof ParameterizedType parameterized
                        && parameterized.getRawType() == TypedEndpointHandler.class) {
                    Type input = parameterized.getActualTypeArguments()[0];
                    return input instanceof Class<?> || input instanceof ParameterizedType ? input : JsonNode.class;
                }
            }
        }
        return JsonNode.class;
    }
}
