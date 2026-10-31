/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.hostfixtures;

import org.springframework.context.annotation.Bean;

import io.runtime.orchestrator.bootstrap.RuntimeShellFatalAction;

/** A standalone Host's configuration that defines only the terminal action the runtime requires of it. */
public class FatalActionOnlyAutoConfiguration {

    @Bean
    public RuntimeShellFatalAction runtimeShellFatalAction() {
        return reason -> { };
    }
}
