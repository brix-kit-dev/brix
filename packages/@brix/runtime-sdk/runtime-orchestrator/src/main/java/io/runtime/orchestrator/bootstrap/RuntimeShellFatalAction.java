/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.bootstrap;

/**
 * Host-owned terminal action invoked after Runtime has closed admission and drained modules.
 *
 * <p>Embedded Hosts normally observe the fatal future and use a no-operation
 * action. Standalone and Local process wrappers must provide an implementation
 * that terminates their process with the configured non-zero exit code. L2B
 * deliberately does not call {@link System#exit(int)}.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
@FunctionalInterface
public interface RuntimeShellFatalAction {

    /**
     * Applies the Host-owned terminal action.
     *
     * @param reason stable fatal reason
     */
    void onFatal(RuntimeShellBootstrapHandle.FatalReason reason);
}
