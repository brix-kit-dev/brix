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
package io.brix.platform.auth.reactive.oauth2;

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
 * OAuth2 bean configuration for the reactive stack.
 *
 * <p>Configures the infrastructure beans required by the OAuth2 login flow:
 * <ul>
 *   <li>{@link WebClient.Builder} — HTTP client for calling third-party IdP APIs
 *       (token exchange, user info retrieval) with sensible timeouts.</li>
 *   <li>Enables {@link OAuth2Properties} configuration binding.</li>
 * </ul>
 *
 * <h3>Timeout Strategy</h3>
 * <p>All timeouts are intentionally shorter than typical server defaults because
 * OAuth2 callbacks represent user-visible latency. Blocking on a slow IdP would
 * degrade perceived login speed.
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 * @since P112
 */
@Configuration
@EnableConfigurationProperties(OAuth2Properties.class)
public class OAuth2Config {

    /**
     * Creates an OAuth2-dedicated {@link WebClient.Builder} with Netty timeouts.
     *
     * <p>Timeouts:
     * <ul>
     *   <li>Connect timeout: 10 seconds</li>
     *   <li>Response timeout: 15 seconds</li>
     *   <li>Read/Write timeout: 15 seconds each</li>
     * </ul>
     *
     * @return configured {@code WebClient.Builder}
     */
    @Bean
    public WebClient.Builder oAuth2WebClientBuilder() {
        HttpClient httpClient = Objects.requireNonNull(HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
            .responseTimeout(Duration.ofSeconds(15))
            .doOnConnected(conn -> conn
                .addHandlerLast(new ReadTimeoutHandler(15, TimeUnit.SECONDS))
                .addHandlerLast(new WriteTimeoutHandler(15, TimeUnit.SECONDS))));

        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
