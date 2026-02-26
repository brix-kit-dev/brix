package io.brix.platform.gateway.config.resilience;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import jakarta.annotation.PostConstruct;

/**
 * 网关弹性配
 * <p>
 * MVP 红线要求
 * <ul>
 *   <li>Redis/HTTP 显式超时</li>
 *   <li>有限重试（最3 次）</li>
 * </ul>
 * </p>
 *
 * <h3>配置层级</h3>
 * <pre>
 * gateway.resilience.http.*    HTTP 下游调用超时
 * gateway.resilience.retry.*   重试策略
 * gateway.resilience.redis.*   Redis 操作超时与重
 * </pre>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@Configuration
@EnableConfigurationProperties({
    HttpTimeoutProperties.class,
    RetryProperties.class,
    RedisResilienceProperties.class
})
public class ResilienceConfig {

    private static final Logger logger = LoggerFactory.getLogger(ResilienceConfig.class);

    private final HttpTimeoutProperties httpTimeoutProperties;
    private final RetryProperties retryProperties;
    private final RedisResilienceProperties redisProperties;

    public ResilienceConfig(HttpTimeoutProperties httpTimeoutProperties,
                           RetryProperties retryProperties,
                           RedisResilienceProperties redisProperties) {
        this.httpTimeoutProperties = httpTimeoutProperties;
        this.retryProperties = retryProperties;
        this.redisProperties = redisProperties;
    }

    @PostConstruct
    public void logConfiguration() {
        logger.info("[shinwa] Gateway Resilience Configuration:");
        logger.info("[shinwa]   HTTP Timeout enabled={}", httpTimeoutProperties.isEnabled());
        if (httpTimeoutProperties.isEnabled()) {
            logger.info("[shinwa]     connect-timeout={}ms, response-timeout={}ms, global-timeout={}ms",
                httpTimeoutProperties.getConnectTimeoutMs(),
                httpTimeoutProperties.getResponseTimeoutMs(),
                httpTimeoutProperties.getGlobalTimeoutMs());
        }
        
        logger.info("[shinwa]   Retry enabled={}", retryProperties.isEnabled());
        if (retryProperties.isEnabled()) {
            logger.info("[shinwa]     max-attempts={}, initial-backoff={}ms, max-backoff={}ms, jitter={}",
                retryProperties.getMaxAttempts(),
                retryProperties.getInitialBackoffMs(),
                retryProperties.getMaxBackoffMs(),
                retryProperties.isJitterEnabled());
        }
        
        logger.info("[shinwa]   Redis Resilience enabled={}", redisProperties.isEnabled());
        if (redisProperties.isEnabled()) {
            logger.info("[shinwa]     command-timeout={}ms, connect-timeout={}ms, max-attempts={}",
                redisProperties.getCommandTimeoutMs(),
                redisProperties.getConnectTimeoutMs(),
                redisProperties.getMaxAttempts());
        }
    }

    /**
     * 配置 Spring Cloud Gateway HTTP 客户端属
     * <p>
     * 将自定义超时配置应用Gateway Netty HttpClient
     * </p>
     */
    @Bean("gatewayHttpClientProperties")
    @Primary
    public HttpClientProperties gatewayHttpClientProperties() {
        HttpClientProperties properties = new HttpClientProperties();
        
        if (httpTimeoutProperties.isEnabled()) {
            // 设置连接超时
            HttpClientProperties.Pool pool = new HttpClientProperties.Pool();
            properties.setPool(pool);
            
            // 设置响应超时
            properties.setResponseTimeout(
                Duration.ofMillis(httpTimeoutProperties.getResponseTimeoutMs())
            );
            
            // 设置连接超时
            properties.setConnectTimeout(httpTimeoutProperties.getConnectTimeoutMs());
            
            logger.debug("[shinwa] Configured HttpClient: connectTimeout={}ms, responseTimeout={}ms",
                httpTimeoutProperties.getConnectTimeoutMs(),
                httpTimeoutProperties.getResponseTimeoutMs());
        }
        
        return properties;
    }
}
