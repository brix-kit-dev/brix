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
package io.brix.platform.gateway.config.resilience;

import java.time.Duration;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;

/**
 * Redis resilienceconfiguration
 * <p>
 * MVP Red Line Requirements：Redis explicittimeoutconfiguration
 * configuration Lettuce Redis clientoftimeoutwithretrystrategy
 * </p>
 *
 * <h3>configuration</h3>
 * <ul>
 *   <li>Command timeout：singleRedis commandofmaximumexecutetime</li>
 *   <li>Connection timeout：establishconnectionofmaximumwaittime</li>
 *   <li>automaticre-connect：connectionlosttimeautomaticre-newestablishconnection</li>
 * </ul>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@Configuration
@ConditionalOnProperty(prefix = "gateway.resilience.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisResilienceConfig {

    private static final Logger logger = LoggerFactory.getLogger(RedisResilienceConfig.class);

    private final RedisResilienceProperties properties;

    public RedisResilienceConfig(RedisResilienceProperties properties) {
        this.properties = properties;
    }

    /**
     * Configure Lettuce clientresource
     * <p>
     * controlthreadpooland I/O resourceofconfiguration
     * </p>
     */
    @Bean(destroyMethod = "shutdown")
    public ClientResources clientResources() {
        return DefaultClientResources.builder()
            .ioThreadPoolSize(4)
            .computationThreadPoolSize(4)
            .build();
    }

    /**
     * customLettuce clientconfiguration
     * <p>
     * configurationtimeout、retry、connectionpooletcparameter
     * </p>
     */
    @Bean
    public LettuceClientConfigurationBuilderCustomizer lettuceClientCustomizer() {
        return builder -> {
            // Command timeout
            builder.commandTimeout(Objects.requireNonNull(Duration.ofMillis(properties.getCommandTimeoutMs())));
            
            // closedtimeout
            builder.shutdownTimeout(Objects.requireNonNull(Duration.ofSeconds(5)));
            
            // ㈡€」
            builder.clientOptions(Objects.requireNonNull(clientOptions()));
            
            // clientresource
            builder.clientResources(Objects.requireNonNull(clientResources()));
            
            logger.info("[brix] Configured Lettuce Redis client: commandTimeout={}ms, connectTimeout={}ms",
                properties.getCommandTimeoutMs(),
                properties.getConnectTimeoutMs());
        };
    }

    /**
     * Lettuce ㈡€」
     * <p>
     * configuration Socket option、timeoutoption、automaticre-connectetc
     * </p>
     */
    private ClientOptions clientOptions() {
        // Socket 」
        SocketOptions socketOptions = SocketOptions.builder()
            .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
            .keepAlive(true)
            .tcpNoDelay(true)
            .build();

        // timeoutoption
        TimeoutOptions timeoutOptions = TimeoutOptions.builder()
            .fixedTimeout(Duration.ofMillis(properties.getCommandTimeoutMs()))
            .build();

        // ㈡€」
        ClientOptions.Builder builder = ClientOptions.builder()
            .socketOptions(socketOptions)
            .timeoutOptions(timeoutOptions)
            .publishOnScheduler(true);
        
        // automaticre-connectconfiguration
        if (properties.isAutoReconnect()) {
            builder.autoReconnect(true);
        } else {
            builder.autoReconnect(false);
        }
        
        // breakopenconnectiontimeoflineis
        builder.disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
        
        return builder.build();
    }
}
