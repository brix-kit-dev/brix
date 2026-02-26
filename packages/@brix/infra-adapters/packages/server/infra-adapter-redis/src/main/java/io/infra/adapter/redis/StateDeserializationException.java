/*
 * Copyright 2026 Brix Authors
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
package io.infra.adapter.redis;

/**
 * 状态存储反序列化异常
 * 
 * <p>当从 Redis 读取的数据无法反序列化为目标类型时抛出此异常。
 * 调用方可据此区分"键不存在"（返回 Optional.empty()）与"值已损坏"（抛出此异常）。</p>
 * 
 * @author Brix Platform Authors
 * @since 3.0.0
 * @see RedisStateStoreCapability
 */
public class StateDeserializationException extends StateStoreException {

    /**
     * 导致反序列化失败的键
     */
    private final String key;

    /**
     * 目标类型
     */
    private final String targetType;

    public StateDeserializationException(String key, String targetType, String message, Throwable cause) {
        super(message, cause);
        this.key = key;
        this.targetType = targetType;
    }

    /**
     * 获取导致反序列化失败的键
     * 
     * @return 存储键
     */
    public String getKey() {
        return key;
    }

    /**
     * 获取目标类型名称
     * 
     * @return 目标类型
     */
    public String getTargetType() {
        return targetType;
    }
}
