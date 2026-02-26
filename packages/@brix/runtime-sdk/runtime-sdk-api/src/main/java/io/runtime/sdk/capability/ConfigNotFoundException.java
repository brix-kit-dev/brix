/*
 * Copyright 2026 Runtime SDK Authors
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
package io.runtime.sdk.capability;

/**
 * 配置未找到异常
 * 
 * <p>当尝试获取必需配置但配置不存在时抛出此异常。</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see ConfigStoreCapability#getRequired(String, Class)
 */
public class ConfigNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 配置键
     */
    private final String configKey;

    /**
     * 创建配置未找到异常
     * 
     * @param message 异常消息
     */
    public ConfigNotFoundException(String message) {
        super(message);
        this.configKey = extractKeyFromMessage(message);
    }

    /**
     * 创建配置未找到异常
     * 
     * @param configKey 配置键
     * @param message   异常消息
     */
    public ConfigNotFoundException(String configKey, String message) {
        super(message);
        this.configKey = configKey;
    }

    /**
     * 获取配置键
     * 
     * @return 配置键
     */
    public String getConfigKey() {
        return configKey;
    }

    /**
     * 从消息中提取配置键
     */
    private static String extractKeyFromMessage(String message) {
        if (message != null && message.contains(":")) {
            return message.substring(message.lastIndexOf(":") + 1).trim();
        }
        return null;
    }
}
