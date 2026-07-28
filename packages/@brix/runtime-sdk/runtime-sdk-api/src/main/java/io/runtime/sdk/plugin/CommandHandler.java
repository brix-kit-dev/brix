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
 * @param <C> command request type
 * @param <R> command result type
 * @author Runtime SDK Team
 * @since 3.0.10
 */
@FunctionalInterface
public interface CommandHandler<C> {

    /**
     * Handles a command invocation accepted by the Runtime Shell.
     *
     * @param invocation immutable command invocation
     * @return command handling completion
     */
    CompletionStage<Void> handle(CommandInvocation<C> invocation);
}
