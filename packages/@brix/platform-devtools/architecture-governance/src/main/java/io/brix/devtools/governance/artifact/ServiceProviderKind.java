/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.artifact;

/**
 * ServiceLoader provider files that must be associated with the same artifact
 * as the active Runtime descriptor.
 */
public enum ServiceProviderKind {
    BRIX_PLUGIN("io.runtime.sdk.plugin.BrixPlugin"),
    PLATFORM_OPERATIONAL_MODULE("io.runtime.orchestrator.operational.PlatformOperationalModule"),
    INTERNAL_CONTRACT_PROVIDER("io.runtime.sdk.internalcontract.InternalContractProvider");

    private final String serviceName;

    ServiceProviderKind(String serviceName) {
        this.serviceName = serviceName;
    }

    public String servicePath() {
        return "META-INF/services/" + serviceName;
    }
}
