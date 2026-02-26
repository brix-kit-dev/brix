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

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * HTTP 通信能力契约
 * 
 * <p>提供跨服务 HTTP 调用的统一抽象，所有模块的 HTTP 通信必须通过此接口进行。
 * 这确保了统一的错误处理、分布式链路追踪、超时控制和可观测性。</p>
 * 
 * <h3>核心职责</h3>
 * <ul>
 *   <li>HTTP GET/POST/PUT/DELETE 请求</li>
 *   <li>统一的请求头和响应处理</li>
 *   <li>连接超时和读取超时管理</li>
 *   <li>分布式追踪上下文传播（由适配器实现）</li>
 * </ul>
 * 
 * <h3>架构约束（红线 R3）</h3>
 * <p>业务模块（Plugin Layer）禁止直接使用任何 HTTP 客户端库
 * （RestTemplate、WebClient、OpenFeign、OkHttp、java.net.http.HttpClient），
 * 必须通过此能力契约进行 HTTP 调用。</p>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Inject
 * private HttpCapability httpClient;
 * 
 * public void callExternalApi() {
 *     HttpResult result = httpClient.get(
 *         "https://api.example.com/data",
 *         Map.of("Authorization", "Bearer token123")
 *     );
 *     
 *     if (result.isSuccess()) {
 *         String data = result.body();
 *         // 处理响应...
 *     }
 * }
 * }</pre>
 * 
 * <h3>实现说明</h3>
 * <ul>
 *   <li>Simple Adapter：使用 JDK HttpClient（开发/测试环境）</li>
 *   <li>Full Product Host：可集成 Spring WebClient + Resilience4j（生产环境）</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.1.0
 * @see HttpResult
 */
public interface HttpCapability {

    /**
     * 发送 HTTP GET 请求
     * 
     * @param url     请求 URL，不能为空
     * @param headers 请求头，可以为空 Map
     * @return HTTP 响应结果
     * @throws HttpCapabilityException 如果请求发送失败（网络错误、超时等）
     */
    HttpResult get(String url, Map<String, String> headers);

    /**
     * 发送 HTTP POST 请求
     * 
     * @param url     请求 URL，不能为空
     * @param body    请求体，可以为 null
     * @param headers 请求头，可以为空 Map
     * @return HTTP 响应结果
     * @throws HttpCapabilityException 如果请求发送失败
     */
    HttpResult post(String url, String body, Map<String, String> headers);

    /**
     * 发送 HTTP PUT 请求
     * 
     * @param url     请求 URL，不能为空
     * @param body    请求体，可以为 null
     * @param headers 请求头，可以为空 Map
     * @return HTTP 响应结果
     * @throws HttpCapabilityException 如果请求发送失败
     */
    HttpResult put(String url, String body, Map<String, String> headers);

    /**
     * 发送 HTTP DELETE 请求
     * 
     * @param url     请求 URL，不能为空
     * @param headers 请求头，可以为空 Map
     * @return HTTP 响应结果
     * @throws HttpCapabilityException 如果请求发送失败
     */
    HttpResult delete(String url, Map<String, String> headers);

    /**
     * 发送无请求头的 GET 请求（便捷方法）
     */
    default HttpResult get(String url) {
        return get(url, Collections.emptyMap());
    }

    /**
     * 发送无请求头的 POST 请求（便捷方法）
     */
    default HttpResult post(String url, String body) {
        return post(url, body, Collections.emptyMap());
    }

    // ==================== 响应结果 ====================

    /**
     * HTTP 响应结果
     * 
     * @param statusCode      HTTP 状态码
     * @param body            响应体字符串
     * @param responseHeaders 响应头
     */
    record HttpResult(
            int statusCode,
            String body,
            Map<String, List<String>> responseHeaders
    ) {
        /**
         * 检查响应是否成功（2xx 状态码）
         */
        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }

        /**
         * 创建只包含状态码和响应体的结果
         */
        public static HttpResult of(int statusCode, String body) {
            return new HttpResult(statusCode, body, Collections.emptyMap());
        }
    }
}
