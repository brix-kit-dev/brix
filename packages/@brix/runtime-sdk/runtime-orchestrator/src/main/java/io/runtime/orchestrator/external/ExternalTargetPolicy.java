/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.external;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Refuses external call targets that resolve to this host (root BRX-RS-ERROR-014, A-11; standard-link plan L8.3).
 *
 * <p>A target is refused when any of its addresses is a loopback, wildcard, link-local or multicast address, an
 * address of one of this host's network interfaces, or an address of a name the host lists as its own
 * ({@code brix.external-http.self-addresses}, for the public address of a host behind a proxy or NAT). Link-local
 * addresses include cloud metadata endpoints.</p>
 *
 * <p>The check runs when a plugin starts and before every attempt, on the addresses the name resolves to then. A
 * name that is rebound between the check and the connection is not caught.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class ExternalTargetPolicy {

    private final Supplier<Set<InetAddress>> localAddresses;
    private final List<String> selfNames;
    private final Resolver resolver;

    /**
     * Creates the policy.
     *
     * @param selfNames the names and addresses the host is reached at from outside
     */
    public ExternalTargetPolicy(Collection<String> selfNames) {
        this(selfNames, refreshed(ExternalTargetPolicy::interfaceAddresses, Duration.ofMinutes(1)),
            InetAddress::getAllByName);
    }

    ExternalTargetPolicy(Collection<String> selfNames, Supplier<Set<InetAddress>> localAddresses, Resolver resolver) {
        this.selfNames = selfNames == null ? List.of()
            : selfNames.stream().filter(name -> name != null && !name.isBlank()).map(String::trim).toList();
        this.localAddresses = Objects.requireNonNull(localAddresses, "localAddresses must not be null");
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
    }

    /**
     * Returns why a host may not be called.
     *
     * @param host the target's host name or address
     * @return the reason, or empty when the host may be called
     * @throws UnknownHostException when the name does not resolve
     */
    public Optional<String> refusal(String host) throws UnknownHostException {
        InetAddress[] addresses = resolver.resolve(host);
        Set<InetAddress> local = localAddresses.get();
        Set<InetAddress> self = new HashSet<>();
        for (String name : selfNames) {
            try {
                Collections.addAll(self, resolver.resolve(name));
            } catch (UnknownHostException e) {
                // A self name that does not resolve here cannot be the target either.
            }
        }
        for (InetAddress address : addresses) {
            if (address.isLoopbackAddress() || address.isAnyLocalAddress()) {
                return Optional.of("loopback");
            }
            if (address.isLinkLocalAddress() || address.isMulticastAddress()) {
                return Optional.of("link-local");
            }
            if (local.contains(address) || self.contains(address)) {
                return Optional.of("this-host");
            }
        }
        return Optional.empty();
    }

    /** Reads the host's interface addresses at most once a {@code period}. */
    private static Supplier<Set<InetAddress>> refreshed(Supplier<Set<InetAddress>> read, Duration period) {
        AtomicReference<Set<InetAddress>> addresses = new AtomicReference<>();
        AtomicLong readAt = new AtomicLong();
        return () -> {
            long now = System.nanoTime();
            Set<InetAddress> current = addresses.get();
            if (current == null || now - readAt.get() > period.toNanos()) {
                current = read.get();
                addresses.set(current);
                readAt.set(now);
            }
            return current;
        };
    }

    private static Set<InetAddress> interfaceAddresses() {
        Set<InetAddress> addresses = new HashSet<>();
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                addresses.addAll(Collections.list(network.getInetAddresses()));
            }
        } catch (SocketException e) {
            // Without the interface list, loopback, link-local and the listed self names still apply.
        }
        return Set.copyOf(addresses);
    }

    /** Resolves a host name to its addresses. */
    @FunctionalInterface
    interface Resolver {

        /**
         * Resolves a name.
         *
         * @param host the name or address
         * @return its addresses
         * @throws UnknownHostException when it does not resolve
         */
        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}
