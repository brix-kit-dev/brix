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
package io.runtime.orchestrator.endpoint;

import java.util.function.Supplier;

/**
 * Runtime policy applied around one endpoint handler invocation.
 *
 * <p>The dispatcher selects the route and builds the invocation; the boundary decides what runs
 * around the handler, for example the owner unit of work of root blueprint BRX-RS-DATA-028.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
@FunctionalInterface
public interface EndpointInvocationBoundary {

    /**
     * Runs the handler call for {@code route}.
     *
     * @param route matched route
     * @param handlerCall invokes the endpoint handler
     * @return the handler result
     */
    Object invoke(EndpointRoute route, Supplier<Object> handlerCall);

    /**
     * Returns a boundary that calls the handler directly.
     *
     * @return the pass-through boundary
     */
    static EndpointInvocationBoundary direct() {
        return (route, handlerCall) -> handlerCall.get();
    }
}
