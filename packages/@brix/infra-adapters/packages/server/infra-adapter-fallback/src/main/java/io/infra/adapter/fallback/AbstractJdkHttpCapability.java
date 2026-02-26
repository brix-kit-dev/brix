/*
 * Copyright 2026 Brix Authors
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
package io.infra.adapter.fallback;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.sdk.capability.HttpCapability;
import io.runtime.sdk.capability.HttpCapabilityException;

/**
 * 基于 JDK HttpClient 的 HTTP 能力抽象基类
 * 
 * <p>提供基于 Java 标准库 {@link java.net.http.HttpClient} 的 HTTP 通信能力公共实现。
 * 子类可以通过继承本类快速实现 {@link HttpCapability} 接口。</p>
 * 
 * <h3>架构说明</h3>
 * <p>
 * 本类属于 {@code infra-adapter-fallback} 模块（Layer 2.5: Adapter 层），
 * 是 HTTP 能力实现的公共基类。以下类继承自本类：
 * <ul>
 *   <li>{@code FallbackHttpCapability} — infra-adapter-fallback 模块中的 Fallback 实现</li>
 *   <li>{@code JdkHttpCapability} — infra-adapter-simple 模块中的简单实现</li>
 * </ul>
 * </p>
 * 
 * <h3>技术特性</h3>
 * <ul>
 *   <li><b>零外部依赖</b> — 仅使用 JDK 11+ 标准库 {@link java.net.http.HttpClient}</li>
 *   <li><b>HTTP/2 支持</b> — 自动协商协议版本（HTTP/1.1 或 HTTP/2）</li>
 *   <li><b>可配置超时</b> — 支持连接超时和请求超时配置</li>
 *   <li><b>线程安全</b> — {@link HttpClient} 实例线程安全，可安全共享</li>
 * </ul>
 * 
 * <h3>超时配置说明</h3>
 * <ul>
 *   <li><b>连接超时（connectTimeout）</b>：建立 TCP 连接的最大等待时间，默认 10 秒</li>
 *   <li><b>请求超时（requestTimeout）</b>：单次 HTTP 请求的最大等待时间，默认 30 秒</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 继承方式
 * @Capability(type = HttpCapability.class, name = "my-http")
 * public class MyHttpCapability extends AbstractJdkHttpCapability {
 *     public MyHttpCapability() {
 *         super(10, 30);
 *     }
 * }
 * 
 * // 直接使用
 * HttpCapability http = new FallbackHttpCapability();
 * HttpResult result = http.get("https://api.example.com/users", 
 *     Map.of("Authorization", "Bearer token"));
 * }</pre>
 * 
 * @author Brix Team
 * @version 3.2.0
 * @since 3.2.0
 * @see HttpCapability
 * @see HttpClient
 * @see FallbackHttpCapability
 */
public abstract class AbstractJdkHttpCapability implements HttpCapability {

    /**
     * 日志记录器
     * 
     * <p>使用子类的类名作为日志名称，便于区分不同实现的日志输出。</p>
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * JDK HttpClient 实例
     * 
     * <p>HttpClient 是线程安全的，可以在多个线程间安全共享。
     * 此实例在构造时创建，生命周期与 Capability 实例相同。</p>
     */
    protected final HttpClient httpClient;

    /**
     * 请求超时时间
     * 
     * <p>用于设置单次 HTTP 请求的最大等待时间。
     * 注意：这不同于连接超时，请求超时包含连接建立后的数据传输时间。</p>
     */
    protected final Duration requestTimeout;

    /**
     * 使用指定配置创建实例
     * 
     * <p>配置说明：
     * <ul>
     *   <li>连接超时：建立 TCP 连接的最大等待时间</li>
     *   <li>请求超时：单次 HTTP 请求的最大等待时间</li>
     * </ul>
     * </p>
     * 
     * @param connectTimeoutSeconds 连接超时秒数（建立 TCP 连接的最大等待时间）
     * @param requestTimeoutSeconds 请求超时秒数（单次 HTTP 请求的最大等待时间）
     */
    protected AbstractJdkHttpCapability(int connectTimeoutSeconds, int requestTimeoutSeconds) {
        // 创建 HttpClient 实例，配置连接超时
        // HttpClient 使用建造者模式，支持链式配置
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
        this.requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
        
        log.info("[{}] 初始化完成: connectTimeout={}s, requestTimeout={}s",
                getClass().getSimpleName(), connectTimeoutSeconds, requestTimeoutSeconds);
    }

    /**
     * 使用默认超时创建实例
     * 
     * <p>使用推荐的默认超时值：连接 10 秒，请求 30 秒。
     * 这些值适合大多数业务场景，避免长时间等待导致的资源占用。</p>
     */
    protected AbstractJdkHttpCapability() {
        this(10, 30);
    }

    /**
     * 执行 HTTP GET 请求
     * 
     * <p>发送 HTTP GET 请求到指定 URL，并返回响应结果。
     * GET 请求用于获取资源，是幂等操作。</p>
     * 
     * @param url 请求 URL（必须是完整的 HTTP/HTTPS URL）
     * @param headers 请求头（可以为 null）
     * @return HTTP 响应结果，包含状态码、响应体和响应头
     * @throws HttpCapabilityException 当请求失败时抛出
     */
    @Override
    public HttpResult get(String url, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(requestTimeout)
                .GET();
        applyHeaders(builder, headers);
        return execute(builder.build());
    }

    /**
     * 执行 HTTP POST 请求
     * 
     * <p>发送 HTTP POST 请求到指定 URL，包含请求体。
     * POST 请求用于创建资源，不是幂等操作。</p>
     * 
     * @param url 请求 URL
     * @param body 请求体（可以为 null，将发送空 body）
     * @param headers 请求头（可以为 null，建议至少设置 Content-Type）
     * @return HTTP 响应结果
     * @throws HttpCapabilityException 当请求失败时抛出
     */
    @Override
    public HttpResult post(String url, String body, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(requestTimeout)
                .POST(bodyPublisher(body));
        applyHeaders(builder, headers);
        return execute(builder.build());
    }

    /**
     * 执行 HTTP PUT 请求
     * 
     * <p>发送 HTTP PUT 请求到指定 URL，包含请求体。
     * PUT 请求用于更新资源，是幂等操作。</p>
     * 
     * @param url 请求 URL
     * @param body 请求体（可以为 null）
     * @param headers 请求头
     * @return HTTP 响应结果
     * @throws HttpCapabilityException 当请求失败时抛出
     */
    @Override
    public HttpResult put(String url, String body, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(requestTimeout)
                .PUT(bodyPublisher(body));
        applyHeaders(builder, headers);
        return execute(builder.build());
    }

    /**
     * 执行 HTTP DELETE 请求
     * 
     * <p>发送 HTTP DELETE 请求到指定 URL。
     * DELETE 请求用于删除资源，是幂等操作。</p>
     * 
     * @param url 请求 URL
     * @param headers 请求头
     * @return HTTP 响应结果
     * @throws HttpCapabilityException 当请求失败时抛出
     */
    @Override
    public HttpResult delete(String url, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(requestTimeout)
                .DELETE();
        applyHeaders(builder, headers);
        return execute(builder.build());
    }

    // ==================== 内部方法 ====================

    /**
     * 执行 HTTP 请求并返回结果
     * 
     * <p>统一的请求执行方法，处理：
     * <ul>
     *   <li>发送请求并等待响应</li>
     *   <li>处理中断异常（恢复中断状态）</li>
     *   <li>将异常转换为 {@link HttpCapabilityException}</li>
     *   <li>记录请求日志（DEBUG 级别）</li>
     * </ul>
     * </p>
     * 
     * @param request 构建好的 HTTP 请求
     * @return HTTP 响应结果
     * @throws HttpCapabilityException 当请求失败时抛出
     */
    protected HttpResult execute(HttpRequest request) {
        try {
            // 记录请求日志（DEBUG 级别，避免生产环境日志过多）
            log.debug("[HTTP] {} {}", request.method(), request.uri());
            
            // 发送同步请求，使用 String 作为响应体处理器
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // 提取响应头（转换为不可变 Map）
            Map<String, List<String>> responseHeaders = response.headers().map();
            
            // 记录响应日志
            log.debug("[HTTP] {} {} -> {}", request.method(), request.uri(), response.statusCode());

            // 返回包装后的结果
            return new HttpResult(response.statusCode(), response.body(), responseHeaders);
            
        } catch (InterruptedException e) {
            // 【关键】处理中断异常：必须恢复中断状态
            // 参考：Java Concurrency in Practice, Chapter 7
            Thread.currentThread().interrupt();
            throw new HttpCapabilityException("HTTP 请求被中断: " + request.uri(), e);
            
        } catch (Exception e) {
            // 将所有其他异常包装为 HttpCapabilityException
            throw new HttpCapabilityException(
                    "HTTP 请求失败: " + request.method() + " " + request.uri() + " - " + e.getMessage(), e);
        }
    }

    /**
     * 应用请求头到 HttpRequest.Builder
     * 
     * <p>将 Map 形式的请求头逐一添加到请求构建器中。
     * 如果 headers 为 null，则不做任何操作。</p>
     * 
     * @param builder 请求构建器
     * @param headers 请求头 Map（可以为 null）
     */
    protected void applyHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        if (headers != null) {
            headers.forEach(builder::header);
        }
    }

    /**
     * 创建请求体发布器
     * 
     * <p>根据 body 是否为 null，返回相应的 BodyPublisher：
     * <ul>
     *   <li>body 非 null：返回字符串发布器</li>
     *   <li>body 为 null：返回空发布器</li>
     * </ul>
     * </p>
     * 
     * @param body 请求体字符串（可以为 null）
     * @return 对应的 BodyPublisher
     */
    protected HttpRequest.BodyPublisher bodyPublisher(String body) {
        return body != null
                ? HttpRequest.BodyPublishers.ofString(body)
                : HttpRequest.BodyPublishers.noBody();
    }
}
