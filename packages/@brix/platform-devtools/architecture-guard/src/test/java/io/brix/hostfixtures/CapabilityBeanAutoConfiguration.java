/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.hostfixtures;

import org.springframework.context.annotation.Bean;

import io.runtime.orchestrator.bootstrap.RuntimeShellFatalAction;

/** A Host configuration that also builds a capability itself, which belongs in an adapter. */
public class CapabilityBeanAutoConfiguration {

    @Bean
    public RuntimeShellFatalAction runtimeShellFatalAction() {
        return reason -> { };
    }

    @Bean
    public Object greetingCapability() {
        return new Object();
    }
}
