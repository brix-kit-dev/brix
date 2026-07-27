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

/**
 * Handler contract for a manifest-declared event subscription.
 *
 * <p>This type lives in {@code io.runtime.sdk.plugin} and is separate from the
 * legacy annotation {@code io.runtime.sdk.annotation.EventHandler}.</p>
 *
 * @param <E> event type
 * @author Runtime SDK Team
 * @since 3.0.10
 */
@FunctionalInterface
public interface EventHandler<E> {

    /**
     * Handles a delivered event.
     *
     * @param event event payload
     */
    void handle(E event);
}
