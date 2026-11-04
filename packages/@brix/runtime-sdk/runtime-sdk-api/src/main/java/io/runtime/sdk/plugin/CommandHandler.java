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
 * Handler contract for a manifest-declared typed command provider.
 *
 * <p>The runtime runs the handler in its plugin's unit of work, bound to the command's tenant, after it has recorded
 * the command in the inbox and taken its business key; the handler's writes commit with them. To reject the command,
 * throw a {@link io.runtime.sdk.error.BrixException} whose code's category is {@code INVALID}, {@code DENIED},
 * {@code NOT_FOUND} or {@code CONFLICT}: the unit of work rolls back, the sender reads {@code REJECTED} with the code,
 * the command is neither retried nor parked, and the key stays free, so a later command with it runs (standard-link
 * plan point 9). Any other failure rolls back too, and the command is retried, then parked.</p>
 *
 * @param <C> command request type
 * @author Runtime SDK Team
 * @since 3.0.10
 */
@FunctionalInterface
public interface CommandHandler<C> {

    /**
     * Handles a command invocation accepted by the Runtime Shell.
     *
     * @param invocation immutable command invocation
     * @return command handling completion; completing it exceptionally is the same as throwing
     */
    CompletionStage<Void> handle(CommandInvocation<C> invocation);
}
