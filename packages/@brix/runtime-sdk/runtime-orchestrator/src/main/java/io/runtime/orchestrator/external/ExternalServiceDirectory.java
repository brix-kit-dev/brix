/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.external;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The host's directory of external services (root BRX-RS-ERROR-014; standard-link plan L8.3): the address of each
 * {@code baseUrlRef}, from {@code brix.external-services.<baseUrlRef>.base-url}.
 *
 * <p>An address is an absolute {@code https} URL, or {@code http} outside the {@code prod} and {@code production}
 * profiles, with a host, no user information, no query and no fragment; its path, if any, is the prefix of every
 * request path. A malformed address stops the host.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class ExternalServiceDirectory {

    private final Map<String, URI> addresses;

    private ExternalServiceDirectory(Map<String, URI> addresses) {
        this.addresses = Map.copyOf(addresses);
    }

    /**
     * Builds the directory.
     *
     * @param baseUrls the configured address of each {@code baseUrlRef}
     * @param production whether the host runs a production profile, which allows {@code https} only
     * @return the directory
     * @throws IllegalArgumentException when an address is malformed
     */
    public static ExternalServiceDirectory of(Map<String, String> baseUrls, boolean production) {
        Map<String, URI> addresses = new LinkedHashMap<>();
        if (baseUrls != null) {
            baseUrls.forEach((ref, url) -> {
                if (url != null && !url.isBlank()) {
                    addresses.put(ref, address(ref, url.trim(), production));
                }
            });
        }
        return new ExternalServiceDirectory(addresses);
    }

    /**
     * Returns an empty directory.
     *
     * @return a directory that has no address
     */
    public static ExternalServiceDirectory empty() {
        return new ExternalServiceDirectory(Map.of());
    }

    /**
     * Returns the address of a {@code baseUrlRef}.
     *
     * @param baseUrlRef the reference
     * @return the address, or empty when the host has none
     */
    public Optional<URI> address(String baseUrlRef) {
        return Optional.ofNullable(addresses.get(baseUrlRef));
    }

    private static URI address(String ref, String url, boolean production) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("brix.external-services." + ref + ".base-url is not a URL", e);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !("http".equals(scheme) && !production)) {
            throw new IllegalArgumentException("brix.external-services." + ref + ".base-url must be "
                + (production ? "an https URL under a production profile" : "an http or https URL"));
        }
        if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("brix.external-services." + ref
                + ".base-url must name a host and no user, query or fragment");
        }
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        try {
            return new URI(scheme, null, uri.getHost(), uri.getPort(), path.isEmpty() ? null : path, null, null);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("brix.external-services." + ref + ".base-url is not a URL", e);
        }
    }

    @Override
    public String toString() {
        return "ExternalServiceDirectory" + Objects.toString(addresses.keySet());
    }
}
