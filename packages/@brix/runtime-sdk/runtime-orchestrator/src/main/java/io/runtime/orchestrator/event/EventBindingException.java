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
package io.runtime.orchestrator.event;

/**
 * 事件绑定异常
 * 
 * <p>当事件处理器绑定失败时抛出，通常由于以下原因：</p>
 * <ul>
 *   <li>处理器类不存在</li>
 *   <li>处理器方法不存在或签名不匹配</li>
 *   <li>无法获取处理器实例</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public class EventBindingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建事件绑定异常
     * 
     * @param message 错误消息
     */
    public EventBindingException(String message) {
        super(message);
    }

    /**
     * 创建事件绑定异常（带原因）
     * 
     * @param message 错误消息
     * @param cause   原因异常
     */
    public EventBindingException(String message, Throwable cause) {
        super(message, cause);
    }
}
