package io.brix.platform.auth.oauth2;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import reactor.netty.http.client.HttpClient;

/**
 * OAuth2 配置类（响应式）
 * <p>
 * 配置 OAuth2 登录所需Bean
 * <ul>
 *   <li>WebClient.Builder: 用于调用第三OAuth2 API（响应式</li>
 *   <li>ObjectMapper: 用于解析 JSON 响应</li>
 *   <li>启用 OAuth2Properties 配置</li>
 * </ul>
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 * @since P112
 */
@Configuration
@EnableConfigurationProperties(OAuth2Properties.class)
public class OAuth2Config {

    /**
     * 创建 OAuth2 专用WebClient.Builder
     * <p>
     * 配置较短的超时时间，避免第三方服务响应慢影响用户体验
     * </p>
     *
     * @return WebClient.Builder 实例
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = Objects.requireNonNull(HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
            .responseTimeout(Duration.ofSeconds(15))
            .doOnConnected(conn -> conn
                .addHandlerLast(new ReadTimeoutHandler(15, TimeUnit.SECONDS))
                .addHandlerLast(new WriteTimeoutHandler(15, TimeUnit.SECONDS))));

        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
