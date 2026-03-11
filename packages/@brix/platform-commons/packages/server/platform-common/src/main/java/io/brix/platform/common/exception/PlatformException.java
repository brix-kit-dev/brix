/*
 * Copyright 2026 Brix Platform Authors
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
package io.brix.platform.common.exception;

import java.io.Serial;

/**
 * <p>Custom business exception, mandatorily bound to {@link PlatformErrorCode},
 * ensuring all exceptions can be mapped to unified response codes.</p>
 * <p>Recommended to throw in domain/application service layer, do not swallow exceptions in infrastructure layer.</p>
 */
public class PlatformException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = -4924589044659651076L;

    private final PlatformErrorCode errorCode;

    public PlatformException(PlatformErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public PlatformException(PlatformErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public PlatformException(PlatformErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public PlatformErrorCode getErrorCode() {
        return errorCode;
    }
}
