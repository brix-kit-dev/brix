/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package io.brix.platform.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Standard error response body — always returned with non-2xx HTTP status.
 *
 * @since 3.2.0
 */
@Schema(name = "ErrorResponse", description = "统一错误响应")
public record ErrorResponseDto(
        @Schema(description = "请求是否成功（错误时恒为 false）", example = "false") boolean success,
        @Schema(description = "机器可读错误码", example = "AUTH_INVALID_CREDENTIALS") String code,
        @Schema(description = "可呈现给用户的错误消息") String message
) {
    public static ErrorResponseDto of(String code, String message) {
        return new ErrorResponseDto(false, code, message);
    }
}
