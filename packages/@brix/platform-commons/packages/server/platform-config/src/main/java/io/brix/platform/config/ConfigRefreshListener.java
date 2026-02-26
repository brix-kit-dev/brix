package io.brix.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.event.EventListener;

/**
 * 配置刷新监听器
 *
 * <p>监听 Spring Cloud 的配置刷新事件，触发本地配置刷新。
 *
 * @since 3.0.0
 */
public class ConfigRefreshListener {

    private static final Logger log = LoggerFactory.getLogger(ConfigRefreshListener.class);

    private final ConfigManager configManager;

    public ConfigRefreshListener(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * 监听配置刷新事件
     *
     * @param event 刷新事件
     */
    @EventListener(RefreshScopeRefreshedEvent.class)
    public void onRefresh(RefreshScopeRefreshedEvent event) {
        log.info("收到配置刷新事件: {}", event.getName());
        configManager.refresh();
    }
}
