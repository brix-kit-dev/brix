package io.brix.platform.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis 路由刷新事件订阅器
 * <p>
 * 监听 Redis 发布的路由刷新事件，触发 Spring Cloud Gateway 重新加载路由定义。
 * 这解决了动态路由注册后网关不自动刷新的问题。
 * </p>
 * 
 * <p><b>工作流程：</b></p>
 * <ol>
 *   <li>插件引擎注册/更新路由后，发布消息到 Redis 频道</li>
 *   <li>本监听器接收消息，发布 Spring Cloud Gateway RefreshRoutesEvent</li>
 *   <li>Gateway CachingRouteLocator 收到事件后重新读取 RouteDefinitionRepository</li>
 *   <li>从 Redis 加载最新的路由定义</li>
 * </ol>
 * 
 * <p><b>【v3.1 品牌名隔离】</b></p>
 * <p>
 * Redis 频道名现在支持通过配置文件自定义，默认为 &quot;brix:gateway:routes:event&quot;。
 * 配置项：{@code brix.gateway.routes.event-channel}
 * </p>
 * 
 * @author Brix Team
 * @version 3.1.0
 * @see GatewayRoutesProperties
 */
@Configuration
public class RedisRouteRefreshSubscriber {

    private static final Logger logger = LoggerFactory.getLogger(RedisRouteRefreshSubscriber.class);

    private final ApplicationEventPublisher eventPublisher;
    private final GatewayRoutesProperties properties;

    /**
     * 构造函数
     * 
     * @param eventPublisher Spring 事件发布器
     * @param properties 网关路由配置属性
     */
    public RedisRouteRefreshSubscriber(
            ApplicationEventPublisher eventPublisher,
            GatewayRoutesProperties properties) {
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    /**
     * 配置 Redis 消息监听容器
     * 
     * @param connectionFactory Redis 连接工厂
     * @return Redis 消息监听容器
     */
    @Bean("brixRouteRefreshListenerContainer")
    public RedisMessageListenerContainer brixRouteRefreshListenerContainer(
            RedisConnectionFactory connectionFactory) {
        
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        
        String eventChannel = properties.getEventChannel();
        String logPrefix = properties.getLogPrefix();
        
        // 订阅路由刷新频道
        container.addMessageListener(brixRouteRefreshMessageListener(), new ChannelTopic(eventChannel));
        
        logger.info("{} Redis route refresh listener registered on channel: {}", logPrefix, eventChannel);
        return container;
    }

    /**
     * 路由刷新消息处理器
     * 
     * @return 消息监听器
     */
    @Bean("brixRouteRefreshMessageListener")
    public MessageListener brixRouteRefreshMessageListener() {
        final String logPrefix = properties.getLogPrefix();
        
        return new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                String messageBody = new String(message.getBody());
                logger.info("{} Received route refresh event: {}", logPrefix, messageBody);
                
                // 处理 JSON 序列化可能带来的引号 (例如 "refresh" -> refresh)
                String normalizedMessage = messageBody.trim();
                if (normalizedMessage.startsWith("\"") && normalizedMessage.endsWith("\"")) {
                    normalizedMessage = normalizedMessage.substring(1, normalizedMessage.length() - 1);
                }
                
                if ("refresh".equals(normalizedMessage)) {
                    // 发布 Spring Cloud Gateway 的刷新事件
                    eventPublisher.publishEvent(new RefreshRoutesEvent(this));
                    logger.info("{} Triggered route refresh via RefreshRoutesEvent", logPrefix);
                }
            }
        };
    }
}
