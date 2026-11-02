/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.brix.samples.fixturealpha.core;

import io.runtime.orchestrator.bootstrap.RuntimeShellFatalAction;

/**
 * A plugin core class that uses the Runtime implementation: forbidden.
 */
public final class AlphaUsesRuntime {

    /**
     * Returns the forbidden type for bytecode import.
     *
     * @return the Runtime implementation type
     */
    public Class<RuntimeShellFatalAction> runtimeType() {
        return RuntimeShellFatalAction.class;
    }
}
