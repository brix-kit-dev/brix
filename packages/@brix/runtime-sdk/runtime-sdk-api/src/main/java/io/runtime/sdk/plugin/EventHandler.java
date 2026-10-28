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
 * Handler contract for a manifest-declared event subscription (root BRX-RS-CAP-025, §5).
 *
 * <p>The runtime decodes the event's payload into {@code E}, the type argument the implementing
 * class gives this interface, so a handler must be a class that names a concrete payload type.
 * Deliveries are at least once: a handler sees an event again after a failure, and the runtime's
 * inbox skips one it has already committed (root MESSAGE-025).</p>
 *
 * <p>This type is separate from the legacy annotation {@code io.runtime.sdk.annotation.EventHandler}.</p>
 *
 * @param <E> payload type
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public interface EventHandler<E> {

    /**
     * Handles a delivered event inside the subscriber owner's unit of work.
     *
     * @param invocation the delivery
     * @return completes when the event has been handled
     */
    CompletionStage<Void> handle(EventInvocation<E> invocation);
}
