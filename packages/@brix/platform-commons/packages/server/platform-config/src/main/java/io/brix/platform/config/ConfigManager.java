package io.brix.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 配置管理器
 *
 * <p>提供配置的统一管理能力，包括：
 * <ul>
 *   <li>配置获取</li>
 *   <li>配置变更监听</li>
 *   <li>配置刷新</li>
 * </ul>
 *
 * @since 3.0.0
 */
public class ConfigManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);

    private final ConfigProperties properties;
    private final Map<String, Object> configCache = new ConcurrentHashMap<>();
    private final Map<String, Consumer<Object>> listeners = new ConcurrentHashMap<>();

    public ConfigManager(ConfigProperties properties) {
        this.properties = properties;
    }

    /**
     * 获取配置值
     *
     * @param key 配置键
     * @param <T> 配置值类型
     * @return 配置值，不存在返回回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig(String key) {
        return (T) configCache.get(key);
    }

    /**
     * 获取配置值，不存在返回回默认值
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @param <T>          配置值类型
     * @return 配置值
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig(String key, T defaultValue) {
        Object value = configCache.get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * 设置配置值
     *
     * @param key   配置键
     * @param value 配置值
     */
    public void setConfig(String key, Object value) {
        Object oldValue = configCache.put(key, value);
        if (oldValue == null || !oldValue.equals(value)) {
            notifyListeners(key, value);
        }
    }

    /**
     * 注册配置变更监听器
     *
     * @param key      配置键
     * @param listener 监听器
     */
    public void addListener(String key, Consumer<Object> listener) {
        listeners.put(key, listener);
        log.debug("注册配置监听器: {}", key);
    }

    /**
     * 移除配置变更监听器
     *
     * @param key 配置键
     */
    public void removeListener(String key) {
        listeners.remove(key);
        log.debug("移除配置监听器: {}", key);
    }

    /**
     * 刷新所有配置
     */
    public void refresh() {
        log.info("刷新所有配置...");
        // 子类或扩展可以实现具体的刷新逻辑
    }

    /**
     * 通知监听器
     */
    private void notifyListeners(String key, Object value) {
        Consumer<Object> listener = listeners.get(key);
        if (listener != null) {
            try {
                listener.accept(value);
                log.debug("配置变更通知成功: {} = {}", key, value);
            } catch (Exception e) {
                log.error("配置变更通知失败: {} = {}", key, value, e);
            }
        }
    }

    public ConfigProperties getProperties() {
        return properties;
    }
}
