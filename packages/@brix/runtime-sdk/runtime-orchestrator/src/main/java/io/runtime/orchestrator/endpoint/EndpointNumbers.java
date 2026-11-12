/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

/**
 * The numbers of a typed endpoint's JSON, as the client wrote them (root BRX-RS-MANIFEST-022; standard-link plan queue
 * item 7, D123, D124).
 *
 * <p>A decimal reads into a {@link BigDecimal} with every digit; it is never rounded to a {@code double}. An exponent
 * cannot make a number longer than the host's parser takes it written out: {@code 1e999999999} is eleven characters,
 * but a billion digits for the request fingerprint to write and for a schema's {@code multipleOf} to divide.</p>
 */
final class EndpointNumbers {

    private EndpointNumbers() {
    }

    /**
     * Returns a reader of the host's mapper that keeps every decimal digit.
     *
     * @param json the host's mapper
     * @return the reader, with the mapper's other settings
     */
    static ObjectReader exactReader(ObjectMapper json) {
        return json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    /**
     * Returns how many digits a number may have written out without an exponent: as many as the host's parser takes in
     * one number.
     *
     * @param json the host's mapper
     * @return the limit
     */
    static int maxDigits(ObjectMapper json) {
        return json.tokenStreamFactory().streamReadConstraints().getMaxNumberLength();
    }

    /**
     * Returns where a request has a number with more digits, written out without its exponent, than the limit.
     *
     * @param request the request
     * @param maxDigits the limit
     * @return JSON pointers to those numbers; empty when there is none
     */
    static List<String> tooLong(JsonNode request, int maxDigits) {
        List<String> paths = new ArrayList<>();
        collect(request, "", maxDigits, paths);
        return paths;
    }

    /**
     * Returns how many digits a number has written out without an exponent: {@code 1e3} has four ({@code 1000}),
     * {@code 1.5e-3} has four ({@code 0.0015}).
     *
     * @param value the number
     * @return its digits
     */
    static long writtenOut(BigDecimal value) {
        long scale = value.scale();
        return Math.max(value.precision() - scale, 0) + Math.max(scale, 0);
    }

    private static void collect(JsonNode node, String path, int maxDigits, List<String> paths) {
        if (node.isBigDecimal()) {
            if (writtenOut(node.decimalValue()) > maxDigits) {
                paths.add(path);
            }
        } else if (node.isObject()) {
            for (Map.Entry<String, JsonNode> property : node.properties()) {
                collect(property.getValue(), path + "/" + referenceToken(property.getKey()), maxDigits, paths);
            }
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                collect(node.get(index), path + "/" + index, maxDigits, paths);
            }
        }
    }

    /** A property name as a JSON pointer writes it (RFC 6901). */
    private static String referenceToken(String name) {
        return name.replace("~", "~0").replace("/", "~1");
    }
}
