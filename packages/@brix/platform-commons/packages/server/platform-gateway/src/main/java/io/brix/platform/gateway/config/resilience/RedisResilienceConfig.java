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
 * Redis 弹性配
 * <p>
 * MVP 红线要求：Redis 显式超时配置
 * 配置 Lettuce Redis 客户端的超时与重试策略
 * </p>
 *
 * <h3>配置</h3>
 * <ul>
 *   <li>命令超时：单Redis 命令的最大执行时</li>
 *   <li>连接超时：建立连接的最大等待时</li>
 *   <li>自动重连：连接丢失时自动重新建立连接</li>
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
     * 配置 Lettuce 客户端资
     * <p>
     * 控制线程池和 I/O 资源的配置
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
     * 自定Lettuce 客户端配
     * <p>
     * 配置超时、重试、连接池等参数
     * </p>
     */
    @Bean
    public LettuceClientConfigurationBuilderCustomizer lettuceClientCustomizer() {
        return builder -> {
            // 命令超时
            builder.commandTimeout(Objects.requireNonNull(Duration.ofMillis(properties.getCommandTimeoutMs())));
            
            // 关闭超时
            builder.shutdownTimeout(Objects.requireNonNull(Duration.ofSeconds(5)));
            
            // 瀹㈡埛绔€夐」
            builder.clientOptions(Objects.requireNonNull(clientOptions()));
            
            // 客户端资
            builder.clientResources(Objects.requireNonNull(clientResources()));
            
            logger.info("[shinwa] Configured Lettuce Redis client: commandTimeout={}ms, connectTimeout={}ms",
                properties.getCommandTimeoutMs(),
                properties.getConnectTimeoutMs());
        };
    }

    /**
     * Lettuce 瀹㈡埛绔€夐」
     * <p>
     * 配置 Socket 选项、超时选项、自动重连等
     * </p>
     */
    private ClientOptions clientOptions() {
        // Socket 閫夐」
        SocketOptions socketOptions = SocketOptions.builder()
            .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
            .keepAlive(true)
            .tcpNoDelay(true)
            .build();

        // 超时选项
        TimeoutOptions timeoutOptions = TimeoutOptions.builder()
            .fixedTimeout(Duration.ofMillis(properties.getCommandTimeoutMs()))
            .build();

        // 鏋勫缓瀹㈡埛绔€夐」
        ClientOptions.Builder builder = ClientOptions.builder()
            .socketOptions(socketOptions)
            .timeoutOptions(timeoutOptions)
            .publishOnScheduler(true);
        
        // 自动重连配置
        if (properties.isAutoReconnect()) {
            builder.autoReconnect(true);
        } else {
            builder.autoReconnect(false);
        }
        
        // 断开连接时的行为
        builder.disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
        
        return builder.build();
    }
}
