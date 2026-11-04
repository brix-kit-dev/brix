/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.idempotency;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import tools.jackson.databind.JsonNode;

/**
 * The fingerprint of a request: the SHA-256, in hex, of its canonical JSON (standard-link plan point 8, idempotency
 * design §4.5). Two requests with the same content have the same fingerprint however their JSON was written, so a key
 * reused with other content tells itself apart from a duplicate.
 *
 * <p>The canonical JSON follows RFC 8785 in its structure: object members sorted by their names' UTF-16 code units,
 * no whitespace, array order kept, strings escaped the RFC's way. Members whose value is null are left out, so a
 * field a newer minor version added and an older sender left out, and the same field sent as null, are one request.
 * A number is written as its exact decimal value with trailing zeros removed ({@code 1}, {@code 1.0} and {@code 1.00}
 * are one number, {@code 1e2} is {@code 100}); unlike RFC 8785 it is not rounded to a double, so two large ids never
 * become one.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class RequestFingerprint {

    private RequestFingerprint() {
    }

    /**
     * Returns the fingerprint of a request.
     *
     * @param request the request as JSON; read numbers with {@code USE_BIG_DECIMAL_FOR_FLOATS}, so decimals keep
     *     every digit
     * @return 64 hex digits
     * @throws IllegalArgumentException when the JSON holds something other than JSON values
     */
    public static String of(JsonNode request) {
        Objects.requireNonNull(request, "request must not be null");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical(request).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException absent) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", absent);
        }
    }

    /**
     * Returns the canonical JSON of a request, which the fingerprint is taken from.
     *
     * @param request the request as JSON
     * @return the canonical JSON
     */
    public static String canonical(JsonNode request) {
        StringBuilder out = new StringBuilder();
        write(Objects.requireNonNull(request, "request must not be null"), out);
        return out.toString();
    }

    private static void write(JsonNode node, StringBuilder out) {
        switch (node.getNodeType()) {
            case OBJECT -> {
                List<Map.Entry<String, JsonNode>> members = new ArrayList<>();
                for (Map.Entry<String, JsonNode> member : node.properties()) {
                    if (!member.getValue().isNull() && !member.getValue().isMissingNode()) {
                        members.add(member);
                    }
                }
                members.sort(Map.Entry.comparingByKey());
                out.append('{');
                for (int i = 0; i < members.size(); i++) {
                    if (i > 0) {
                        out.append(',');
                    }
                    string(members.get(i).getKey(), out);
                    out.append(':');
                    write(members.get(i).getValue(), out);
                }
                out.append('}');
            }
            case ARRAY -> {
                out.append('[');
                for (int i = 0; i < node.size(); i++) {
                    if (i > 0) {
                        out.append(',');
                    }
                    write(node.get(i), out);
                }
                out.append(']');
            }
            case STRING -> string(node.asString(), out);
            case NUMBER -> out.append(number(node.decimalValue()));
            case BOOLEAN -> out.append(node.booleanValue());
            case NULL -> out.append("null");
            default -> throw new IllegalArgumentException("not a JSON value: " + node.getNodeType());
        }
    }

    private static String number(BigDecimal value) {
        return value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString();
    }

    /** Escapes as RFC 8785 does: the two-character forms where JSON has them, otherwise backslash-u for controls. */
    private static void string(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
