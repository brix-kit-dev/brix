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
package io.runtime.sdk.event;

/**
 * Reliability level declared for an integration event in the plugin manifest.
 *
 * <p>The manifest is the source of truth for reliability. Runtime Shell
 * providers use this value to select the publish path and startup gates without
 * exposing broker, outbox, or repository implementation details to plugins.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public enum EventReliability {

    /**
     * Critical business fact that must use transactional outbox and persistent inbox.
     */
    CRITICAL,

    /**
     * Standard business fact that needs eventual delivery through outbox and inbox.
     */
    STANDARD,

    /**
     * Non-critical fact or signal where loss is acceptable.
     */
    BEST_EFFORT
}
