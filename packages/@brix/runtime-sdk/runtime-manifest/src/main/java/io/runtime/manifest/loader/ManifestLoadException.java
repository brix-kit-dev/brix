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
package io.runtime.manifest.loader;

/**
 * Manifest Load Exception.
 *
 * <p>Thrown when manifest file loading fails.</p>
 * <p>当 Manifest 文件加载失败时抛出。</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public class ManifestLoadException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a Manifest load exception.
     *
     * @param message Exception message
     */
    public ManifestLoadException(String message) {
        super(message);
    }

    /**
     * Creates a Manifest load exception.
     *
     * @param message Exception message
     * @param cause Cause exception
     */
    public ManifestLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
