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
package io.runtime.sdk.plugin;

import java.util.concurrent.CompletionStage;

/**
 * Handler contract for a manifest-declared typed query provider (root BRX-RS-MANIFEST-027).
 *
 * <p>The runtime calls it in the provider's own read-only unit of work, with the caller's transaction suspended,
 * and waits for the returned stage until the invocation's deadline. The handler reads only its own plugin's data
 * and has no side effects: it publishes no event, submits no command and writes nothing (MESSAGE-002, MESSAGE-003).
 * A query that may find nothing returns {@code Optional.empty()}, never {@code null}.</p>
 *
 * <p>Since 3.3.0 the method takes a {@link QueryInvocation} and returns a stage, as the frozen ABI requires; the
 * earlier {@code R handle(Q)} was never routed to any caller.</p>
 *
 * @param <Q> query request type
 * @param <R> query result type, {@code Optional<...>} for a query that may find nothing
 * @author Runtime SDK Team
 * @since 3.0.10
 */
@FunctionalInterface
public interface QueryHandler<Q, R> {

    /**
     * Handles one call of the query.
     *
     * @param invocation the request with the caller, scope, access decision, trace and deadline of the call
     * @return the result, usually already completed
     */
    CompletionStage<R> handle(QueryInvocation<Q> invocation);
}
