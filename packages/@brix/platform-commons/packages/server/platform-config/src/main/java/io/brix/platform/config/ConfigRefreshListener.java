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
package io.brix.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.event.EventListener;

/**
 * Configuration refresh listener.
 *
 * <p>Listens to Spring Cloud configuration refresh events and triggers local configuration refresh.
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
     * Listen for configuration refresh events.
     *
     * @param event refresh event
     */
    @EventListener(RefreshScopeRefreshedEvent.class)
    public void onRefresh(RefreshScopeRefreshedEvent event) {
        log.info("Received configuration refresh event: {}", event.getName());
        configManager.refresh();
    }
}
