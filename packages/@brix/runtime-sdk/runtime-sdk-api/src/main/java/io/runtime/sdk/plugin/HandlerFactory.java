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
 * Creates a handler with the plugin's scoped context (root BRX-RS-CAP-025, lifecycle step P4/O4).
 *
 * @param <H> handler type
 * @since 3.2.0
 */
@FunctionalInterface
public interface HandlerFactory<H> {

    /**
     * Creates the handler.
     *
     * @param context the plugin's scoped context
     * @return the handler
     */
    H create(PluginContext context);
}
