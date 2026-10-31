/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.external;

import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.runtime.orchestrator.invocation.RuntimeInvocations;
import io.runtime.sdk.capability.BrixExternalHttpException;
import io.runtime.sdk.capability.ExternalHttpCapability;
import io.runtime.sdk.capability.ExternalHttpModules;
import io.runtime.sdk.capability.ExternalHttpRequest;
import io.runtime.sdk.capability.ExternalHttpResponse;
import io.runtime.sdk.query.BrixQueryException;

/**
 * The host's external calls (standard-link plan L8): the services each started module declares, the host's
 * directory of their addresses, and the gateway that runs the calls.
 *
 * <p>A module's services are checked when it starts: a declared service whose address resolves to this host stops
 * the module. A service the host's directory has no address for starts, and its calls fail with
 * {@code external_http.undeclared_service}.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class ExternalHttpRuntime implements ExternalHttpModules {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD");

    private final ExternalServiceDirectory directory;
    private final ExternalHttpGateway gateway;
    private final Map<String, Map<String, ExternalServiceDeclaration>> modules = new ConcurrentHashMap<>();

    /**
     * Creates the runtime.
     *
     * @param directory the host's directory
     * @param gateway runs the calls
     */
    public ExternalHttpRuntime(ExternalServiceDirectory directory, ExternalHttpGateway gateway) {
        this.directory = Objects.requireNonNull(directory, "directory must not be null");
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
    }

    /**
     * Returns a runtime with an empty directory, for hosts and tests whose modules call no external service.
     *
     * @return the runtime
     */
    public static ExternalHttpRuntime none() {
        return new ExternalHttpRuntime(ExternalServiceDirectory.empty(), new ExternalHttpGateway(
            ExternalHttpPolicies.Limits.defaults(), new JdkExternalHttpTransport(), new ExternalTargetPolicy(List.of()),
            ExternalHttpTracing.NOOP, ExternalHttpMetrics.NOOP));
    }

    /**
     * Admits a module's declared services, when the module starts.
     *
     * @param moduleId the module
     * @param services its declarations
     * @throws IllegalStateException when a service's address resolves to this host
     */
    public void register(String moduleId, Collection<ExternalServiceDeclaration> services) {
        Map<String, ExternalServiceDeclaration> declared = new LinkedHashMap<>();
        for (ExternalServiceDeclaration service : services) {
            Optional<URI> address = directory.address(service.baseUrlRef());
            Optional<String> refused = address.flatMap(gateway::refusal);
            if (refused.isPresent()) {
                throw new IllegalStateException("external service '" + service.id() + "' of " + moduleId
                    + " resolves to this host (" + refused.get() + "); modules reach each other by typed queries, "
                    + "commands and events");
            }
            declared.put(service.id(), service);
        }
        modules.put(moduleId, Map.copyOf(declared));
    }

    /**
     * Forgets a module's services, when it stops.
     *
     * @param moduleId the module
     */
    public void unregister(String moduleId) {
        modules.remove(moduleId);
    }

    @Override
    public ExternalHttpCapability forModule(String moduleId) {
        Objects.requireNonNull(moduleId, "moduleId must not be null");
        return serviceId -> service(moduleId, serviceId);
    }

    private ExternalHttpCapability.ExternalService service(String moduleId, String serviceId) {
        ExternalServiceDeclaration declared = modules.getOrDefault(moduleId, Map.of()).get(serviceId);
        if (declared == null) {
            throw new BrixExternalHttpException(BrixExternalHttpException.Code.UNDECLARED_SERVICE, serviceId, null);
        }
        URI address = directory.address(declared.baseUrlRef()).orElseThrow(() ->
            new BrixExternalHttpException(BrixExternalHttpException.Code.UNDECLARED_SERVICE, serviceId, null));
        return new ExternalHttpCapability.ExternalService() {
            @Override
            public String id() {
                return serviceId;
            }

            @Override
            public ExternalHttpResponse send(ExternalHttpRequest request) {
                Objects.requireNonNull(request, "request must not be null");
                // Root BRX-RS-MESSAGE-002: a query reads; a write to an external service is a side effect.
                if (RuntimeInvocations.inQuery() && !SAFE_METHODS.contains(request.method())) {
                    throw new BrixQueryException(BrixQueryException.Code.PROVIDER_FAILED,
                        Map.of("reason", "side-effect-in-query", "capability", "ExternalHttpCapability"), null);
                }
                return gateway.send(moduleId, declared, address, request);
            }
        };
    }
}
