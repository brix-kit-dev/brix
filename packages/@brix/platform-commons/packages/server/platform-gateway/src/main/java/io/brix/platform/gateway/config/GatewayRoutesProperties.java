package io.brix.platform.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关路由配置属性
 * <p>
 * 支持通过配置文件自定义 Redis key 前缀等参数
 * </p>
 * 
 * <p><b>【v3.1 品牌名隔离】</b></p>
 * <p>
 * 将硬编码的 `shinwa:gateway:routes` 改为可配置前缀，
 * 支持不同部署环境使用不同的 Redis key 命名空间。
 * </p>
 * 
 * <p><b>配置示例：</b></p>
 * <pre>
 * brix:
 *   gateway:
 *     routes:
 *       key-prefix: "myapp:gateway:routes"
 *       event-channel: "myapp:gateway:routes:event"
 * </pre>
 * 
 * @author Brix Team
 * @version 3.1.0
 * @see GatewayRouteConfig
 * @see RedisRouteRefreshSubscriber
 */
@ConfigurationProperties(prefix = "brix.gateway.routes")
public class GatewayRoutesProperties {

    /**
     * Redis Hash Key 用于存储动态路由
     * <p>
     * 默认值: brix:gateway:routes
     * </p>
     */
    private String keyPrefix = "brix:gateway:routes";

    /**
     * Redis Pub/Sub 频道用于路由刷新事件
     * <p>
     * 默认值: brix:gateway:routes:event
     * </p>
     */
    private String eventChannel = "brix:gateway:routes:event";

    /**
     * 日志前缀标识
     * <p>
     * 默认值: [brix]
     * </p>
     */
    private String logPrefix = "[brix]";

    // ========================================================================
    // Getters & Setters
    // ========================================================================

    /**
     * 获取 Redis Hash Key 前缀
     * 
     * @return Redis key 前缀
     */
    public String getKeyPrefix() {
        return keyPrefix;
    }

    /**
     * 设置 Redis Hash Key 前缀
     * 
     * @param keyPrefix Redis key 前缀
     */
    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    /**
     * 获取 Redis 事件频道名
     * 
     * @return 事件频道名
     */
    public String getEventChannel() {
        return eventChannel;
    }

    /**
     * 设置 Redis 事件频道名
     * 
     * @param eventChannel 事件频道名
     */
    public void setEventChannel(String eventChannel) {
        this.eventChannel = eventChannel;
    }

    /**
     * 获取日志前缀
     * 
     * @return 日志前缀
     */
    public String getLogPrefix() {
        return logPrefix;
    }

    /**
     * 设置日志前缀
     * 
     * @param logPrefix 日志前缀
     */
    public void setLogPrefix(String logPrefix) {
        this.logPrefix = logPrefix;
    }
}
