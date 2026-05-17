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
package io.runtime.sdk.capability;

/**
 * Thrown by {@link ViewModeCapability#switchTo} when the caller is not
 * authorized to switch view mode — typically because the current session
 * does not hold the {@code platform-admin} role.
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public class ViewModeSwitchDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ViewModeSwitchDeniedException(String message) {
        super(message);
    }

    public ViewModeSwitchDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
