package io.infra.adapter.identity.provider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A minimal browser for protocol tests: keeps cookies like a browser on a secure context
 * (it sends {@code Secure} cookies to {@code http://localhost}), never follows redirects by
 * itself and records everything it receives, so tests can check that no token reached it.
 */
final class Browser {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    final Map<String, String> cookies = new LinkedHashMap<>();
    final Map<String, String> setCookieLines = new LinkedHashMap<>();
    final List<String> received = new ArrayList<>();

    Response get(String url, String... headers) {
        return send(HttpRequest.newBuilder(URI.create(url)).GET(), headers);
    }

    Response post(String url, String json, String... headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(json == null ? "" : json))
                .header("Content-Type", "application/json");
        return send(builder, headers);
    }

    /** Follows redirects on this origin, like a browser navigating; returns the first non-redirect. */
    Response navigate(String url) {
        Response response = get(url, "Accept", "text/html");
        int hops = 0;
        while (response.status() == 302 && hops++ < 10) {
            response = get(response.location(), "Accept", "text/html");
        }
        return response;
    }

    private Response send(HttpRequest.Builder builder, String... headers) {
        for (int i = 0; i < headers.length; i += 2) {
            builder.header(headers[i], headers[i + 1]);
        }
        if (!cookies.isEmpty()) {
            builder.header("Cookie", cookies.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining("; ")));
        }
        try {
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            for (String line : response.headers().allValues("set-cookie")) {
                String pair = line.split(";", 2)[0];
                String name = pair.substring(0, pair.indexOf('='));
                String value = pair.substring(pair.indexOf('=') + 1);
                setCookieLines.put(name, line);
                if (value.isEmpty() || line.toLowerCase().contains("max-age=0")) {
                    cookies.remove(name);
                } else {
                    cookies.put(name, value);
                }
            }
            received.add(response.statusCode() + " " + response.headers().map() + "\n" + response.body());
            return new Response(response.statusCode(), response.headers().map(), response.body());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    record Response(int status, Map<String, List<String>> headers, String body) {

        String header(String name) {
            return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .flatMap(entry -> entry.getValue().stream()).findFirst().orElse(null);
        }

        String location() {
            return header("location");
        }

        JsonNode json() {
            return JSON.readTree(body);
        }
    }
}
