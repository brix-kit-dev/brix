/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The path template of an endpoint, and how request paths match it (root BRX-RS-MANIFEST-023; standard-link plan
 * L7.4, D59).
 *
 * <p>A template is a list of segments. A segment is either literal text or a variable that takes the whole segment,
 * such as {@code {tenantId}}. Duplicate and trailing slashes are dropped and percent-encodings decoded, both in
 * templates and in request paths.</p>
 *
 * <p>Two templates are the same route when they differ only in variable names, slashes, letter case or encoding:
 * {@link #identity()} is equal for both, and the Host refuses to start. A request matches a literal segment exactly,
 * after decoding. When several templates match a request, the one with a literal segment where the others have a
 * variable, at the first segment where they differ, wins: {@code /tenants/by-code/{code}} answers
 * {@code /tenants/by-code/members} rather than {@code /tenants/{tenantId}/members}. Templates that tie this way have
 * the same identity, so a started Host never has to choose between them.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class EndpointPathTemplate {

    private static final Pattern VARIABLE = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]*)}");

    private final List<Segment> segments;
    private final String identity;

    private EndpointPathTemplate(List<Segment> segments) {
        this.segments = List.copyOf(segments);
        StringBuilder key = new StringBuilder();
        for (Segment segment : this.segments) {
            key.append('/').append(segment.variable() ? "{}" : segment.text().toLowerCase(Locale.ROOT));
        }
        this.identity = key.isEmpty() ? "/" : key.toString();
    }

    /**
     * Parses a declared template.
     *
     * @param path the declared path, starting with {@code /}
     * @return the template
     * @throws IllegalArgumentException when a segment mixes text and a variable, a variable name repeats, an encoding
     *     is broken, or a segment is {@code .} or {@code ..} or holds a slash, {@code ?} or {@code #}
     */
    public static EndpointPathTemplate parse(String path) {
        if (path == null || !path.startsWith("/")) {
            throw new IllegalArgumentException("an endpoint path starts with /: " + path);
        }
        List<Segment> segments = new ArrayList<>();
        Set<String> variables = new HashSet<>();
        for (String part : path.split("/")) {
            if (part.isEmpty()) {
                continue;
            }
            Matcher variable = VARIABLE.matcher(part);
            if (variable.matches()) {
                if (!variables.add(variable.group(1))) {
                    throw new IllegalArgumentException("path variable {" + variable.group(1) + "} appears twice");
                }
                segments.add(new Segment(variable.group(1), true));
                continue;
            }
            if (part.indexOf('{') >= 0 || part.indexOf('}') >= 0) {
                throw new IllegalArgumentException("path segment '" + part + "' is not a variable: a variable takes "
                    + "the whole segment and its name is a letter followed by letters, digits or _");
            }
            String text = decode(part).orElseThrow(() ->
                new IllegalArgumentException("path segment '" + part + "' has a broken percent-encoding"));
            if (!literal(text)) {
                throw new IllegalArgumentException("path segment '" + part + "' cannot be matched by a request");
            }
            segments.add(new Segment(text, false));
        }
        return new EndpointPathTemplate(segments);
    }

    /**
     * Splits a request path as the client sent it into decoded segments.
     *
     * @param rawPath the undecoded request path
     * @return the segments, or empty when the path has a broken encoding or a {@code .} or {@code ..} segment and
     *     therefore matches no endpoint
     */
    public static Optional<List<String>> requestSegments(String rawPath) {
        if (rawPath == null) {
            return Optional.empty();
        }
        List<String> segments = new ArrayList<>();
        for (String part : rawPath.split("/")) {
            if (part.isEmpty()) {
                continue;
            }
            Optional<String> text = decode(part);
            if (text.isEmpty() || ".".equals(text.get()) || "..".equals(text.get())) {
                return Optional.empty();
            }
            segments.add(text.get());
        }
        return Optional.of(List.copyOf(segments));
    }

    /**
     * Returns the template's identity: literals in lower case and decoded, variables as {@code {}}.
     *
     * @return the identity, such as {@code /api/v1/tenants/{}/members}
     */
    public String identity() {
        return identity;
    }

    /**
     * Tells whether the identity starts with the given literal segments.
     *
     * @param prefix lower-case literal segments
     * @return whether it does
     */
    public boolean startsWith(String... prefix) {
        if (prefix.length > segments.size()) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            Segment segment = segments.get(i);
            if (segment.variable() || !segment.text().toLowerCase(Locale.ROOT).equals(prefix[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Matches decoded request segments.
     *
     * @param request the segments of {@link #requestSegments}
     * @return the path variables, or empty when the request does not match
     */
    public Optional<Map<String, String>> match(List<String> request) {
        if (request.size() != segments.size()) {
            return Optional.empty();
        }
        Map<String, String> variables = new LinkedHashMap<>();
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            if (segment.variable()) {
                variables.put(segment.text(), request.get(i));
            } else if (!segment.text().equals(request.get(i))) {
                return Optional.empty();
            }
        }
        return Optional.of(Map.copyOf(variables));
    }

    /**
     * Orders two templates that match the same request: the one with a literal segment where the other has a
     * variable, at the first segment where they differ, comes first.
     *
     * @param first one template
     * @param second another template of the same length
     * @return a negative number when {@code first} wins, a positive one when {@code second} wins, 0 when they tie
     */
    public static int compare(EndpointPathTemplate first, EndpointPathTemplate second) {
        int length = Math.min(first.segments.size(), second.segments.size());
        for (int i = 0; i < length; i++) {
            boolean firstVariable = first.segments.get(i).variable();
            if (firstVariable != second.segments.get(i).variable()) {
                return firstVariable ? 1 : -1;
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return identity;
    }

    private static boolean literal(String text) {
        return !".".equals(text) && !"..".equals(text) && text.indexOf('/') < 0 && text.indexOf('?') < 0
            && text.indexOf('#') < 0;
    }

    private static Optional<String> decode(String part) {
        if (part.indexOf('%') < 0) {
            return Optional.of(part);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(part.length());
        int i = 0;
        while (i < part.length()) {
            if (part.charAt(i) != '%') {
                int end = part.indexOf('%', i);
                byte[] text = part.substring(i, end < 0 ? part.length() : end).getBytes(StandardCharsets.UTF_8);
                bytes.write(text, 0, text.length);
                i = end < 0 ? part.length() : end;
                continue;
            }
            if (i + 2 >= part.length()) {
                return Optional.empty();
            }
            int high = Character.digit(part.charAt(i + 1), 16);
            int low = Character.digit(part.charAt(i + 2), 16);
            if (high < 0 || low < 0) {
                return Optional.empty();
            }
            bytes.write((high << 4) + low);
            i += 3;
        }
        try {
            return Optional.of(StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes.toByteArray()))
                .toString());
        } catch (CharacterCodingException e) {
            return Optional.empty();
        }
    }

    private record Segment(String text, boolean variable) {
    }
}
