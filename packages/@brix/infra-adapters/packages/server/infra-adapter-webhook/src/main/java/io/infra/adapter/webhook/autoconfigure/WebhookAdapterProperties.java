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
package io.infra.adapter.webhook.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Webhook 适配器配置属性
 * 
 * <p>Spring Boot 配置属性类，用于绑定 application.yml 中的 Webhook 配置。</p>
 * 
 * <h2>配置示例</h2>
 * <pre>{@code
 * brix:
 *   infra:
 *     webhook:
 *       enabled: true
 *       default-endpoint: https://api.example.com/webhook
 *       secret: your-secret-key
 *       signature-enabled: true
 *       connect-timeout: 5s
 *       read-timeout: 30s
 *       max-retries: 3
 *       retry-delay: 1s
 *       endpoint-mappings:
 *         "order.*": https://order-service/webhook
 *         "user.*": https://user-service/webhook
 *       custom-headers:
 *         X-API-Key: your-api-key
 * }</pre>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
@ConfigurationProperties(prefix = "brix.infra.webhook")
public class WebhookAdapterProperties {
    
    /**
     * 是否启用 Webhook 适配器
     */
    private boolean enabled = false;
    
    /**
     * 默认 Webhook 端点 URL
     */
    private String defaultEndpoint;
    
    /**
     * 签名密钥
     */
    private String secret;
    
    /**
     * 是否启用签名验证
     */
    private boolean signatureEnabled = true;
    
    /**
     * 连接超时时间
     */
    private Duration connectTimeout = Duration.ofSeconds(5);
    
    /**
     * 读取超时时间
     */
    private Duration readTimeout = Duration.ofSeconds(30);
    
    /**
     * 最大重试次数
     */
    private int maxRetries = 3;
    
    /**
     * 重试基础延迟
     */
    private Duration retryDelay = Duration.ofSeconds(1);
    
    /**
     * 事件类型到端点的映射
     */
    private Map<String, String> endpointMappings = new HashMap<>();
    
    /**
     * 自定义请求头
     */
    private Map<String, String> customHeaders = new HashMap<>();
    
    /**
     * 时间戳容差（秒），用于签名验证
     */
    private long timestampTolerance = 300;
    
    // ========== Getter / Setter ==========
    
    /**
     * 是否启用 Webhook 适配器
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * 设置是否启用 Webhook 适配器
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /**
     * 获取默认端点 URL
     *
     * @return 默认端点 URL
     */
    public String getDefaultEndpoint() {
        return defaultEndpoint;
    }
    
    /**
     * 设置默认端点 URL
     *
     * @param defaultEndpoint 默认端点 URL
     */
    public void setDefaultEndpoint(String defaultEndpoint) {
        this.defaultEndpoint = defaultEndpoint;
    }
    
    /**
     * 获取签名密钥
     *
     * @return 签名密钥
     */
    public String getSecret() {
        return secret;
    }
    
    /**
     * 设置签名密钥
     *
     * @param secret 签名密钥
     */
    public void setSecret(String secret) {
        this.secret = secret;
    }
    
    /**
     * 是否启用签名验证
     *
     * @return 是否启用签名
     */
    public boolean isSignatureEnabled() {
        return signatureEnabled;
    }
    
    /**
     * 设置是否启用签名验证
     *
     * @param signatureEnabled 是否启用签名
     */
    public void setSignatureEnabled(boolean signatureEnabled) {
        this.signatureEnabled = signatureEnabled;
    }
    
    /**
     * 获取连接超时时间
     *
     * @return 连接超时时间
     */
    public Duration getConnectTimeout() {
        return connectTimeout;
    }
    
    /**
     * 设置连接超时时间
     *
     * @param connectTimeout 连接超时时间
     */
    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }
    
    /**
     * 获取读取超时时间
     *
     * @return 读取超时时间
     */
    public Duration getReadTimeout() {
        return readTimeout;
    }
    
    /**
     * 设置读取超时时间
     *
     * @param readTimeout 读取超时时间
     */
    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
    
    /**
     * 获取最大重试次数
     *
     * @return 最大重试次数
     */
    public int getMaxRetries() {
        return maxRetries;
    }
    
    /**
     * 设置最大重试次数
     *
     * @param maxRetries 最大重试次数
     */
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
    
    /**
     * 获取重试延迟
     *
     * @return 重试延迟
     */
    public Duration getRetryDelay() {
        return retryDelay;
    }
    
    /**
     * 设置重试延迟
     *
     * @param retryDelay 重试延迟
     */
    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay;
    }
    
    /**
     * 获取端点映射
     *
     * @return 端点映射
     */
    public Map<String, String> getEndpointMappings() {
        return endpointMappings;
    }
    
    /**
     * 设置端点映射
     *
     * @param endpointMappings 端点映射
     */
    public void setEndpointMappings(Map<String, String> endpointMappings) {
        this.endpointMappings = endpointMappings;
    }
    
    /**
     * 获取自定义请求头
     *
     * @return 自定义请求头
     */
    public Map<String, String> getCustomHeaders() {
        return customHeaders;
    }
    
    /**
     * 设置自定义请求头
     *
     * @param customHeaders 自定义请求头
     */
    public void setCustomHeaders(Map<String, String> customHeaders) {
        this.customHeaders = customHeaders;
    }
    
    /**
     * 获取时间戳容差
     *
     * @return 时间戳容差（秒）
     */
    public long getTimestampTolerance() {
        return timestampTolerance;
    }
    
    /**
     * 设置时间戳容差
     *
     * @param timestampTolerance 时间戳容差（秒）
     */
    public void setTimestampTolerance(long timestampTolerance) {
        this.timestampTolerance = timestampTolerance;
    }
}
