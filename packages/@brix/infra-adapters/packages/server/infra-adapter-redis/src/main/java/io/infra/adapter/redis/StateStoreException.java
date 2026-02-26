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
 * 状态存储异常
 * 
 * <p>当状态存储操作失败时抛出此异常。</p>
 * 
 * @author Brix Platform Authors Platform Team
 * @since 3.0.0
 */
public class StateStoreException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造函数
     * 
     * @param message 错误消息
     */
    public StateStoreException(String message) {
        super(message);
    }

    /**
     * 带原因的构造函数
     * 
     * @param message 错误消息
     * @param cause   原始异常
     */
    public StateStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
