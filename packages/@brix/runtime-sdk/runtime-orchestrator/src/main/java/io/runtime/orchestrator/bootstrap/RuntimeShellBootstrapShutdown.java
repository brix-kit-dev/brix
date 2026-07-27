/*
 * Copyright 2026 Runtime SDK Authors
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
package io.runtime.orchestrator.bootstrap;

import java.util.Objects;

import org.springframework.beans.factory.DisposableBean;

/**
 * Spring lifecycle bridge that stops Runtime Shell during Host shutdown.
 *
 * <p>This class belongs to L2B Runtime, keeping plugin stop orchestration out of
 * Host source while still making shutdown deterministic for Spring Boot Hosts.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class RuntimeShellBootstrapShutdown implements DisposableBean {

    private final RuntimeShellBootstrap bootstrap;

    /**
     * Creates a Runtime Shell shutdown bridge.
     *
     * @param bootstrap Runtime Shell bootstrap API
     */
    public RuntimeShellBootstrapShutdown(RuntimeShellBootstrap bootstrap) {
        this.bootstrap = Objects.requireNonNull(bootstrap, "bootstrap must not be null");
    }

    /**
     * Stops the Runtime Shell plugin chain.
     */
    @Override
    public void destroy() {
        bootstrap.stop();
    }
}
