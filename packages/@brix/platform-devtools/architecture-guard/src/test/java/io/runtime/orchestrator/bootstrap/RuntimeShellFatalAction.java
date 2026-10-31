/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.runtime.orchestrator.bootstrap;

/** Stand-in for the runtime's terminal action, under its real name: the guard does not depend on the runtime. */
public interface RuntimeShellFatalAction {

    void onFatal(Object reason);
}
