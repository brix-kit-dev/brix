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
 * HTTP 能力调用异常
 * 
 * <p>当 {@link HttpCapability} 的请求发送失败时抛出此异常。
 * 常见原因包括网络错误、连接超时、DNS 解析失败等。</p>
 * 
 * <p>注意：HTTP 4xx/5xx 响应不会抛出此异常，而是正常返回
 * {@link HttpCapability.HttpResult}，由调用方根据状态码处理。</p>
 * 
 * @author Runtime SDK Team
 * @since 3.1.0
 */
public class HttpCapabilityException extends RuntimeException {

    public HttpCapabilityException(String message) {
        super(message);
    }

    public HttpCapabilityException(String message, Throwable cause) {
        super(message, cause);
    }
}
