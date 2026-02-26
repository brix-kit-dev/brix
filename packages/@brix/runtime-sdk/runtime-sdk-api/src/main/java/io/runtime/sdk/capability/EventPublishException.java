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
package io.runtime.sdk.capability;

/**
 * 事件发布异常
 * 
 * <p>当事件发布失败时抛出此异常，调用方可根据此异常进行重试或补偿处理。</p>
 * 
 * @author Brix Platform Authors
 * @since 3.0.0
 * @see EventBusCapability
 */
public class EventPublishException extends RuntimeException {

    /**
     * 失败的事件 ID（如果可用）
     */
    private final String eventId;

    public EventPublishException(String message) {
        super(message);
        this.eventId = null;
    }

    public EventPublishException(String message, Throwable cause) {
        super(message, cause);
        this.eventId = null;
    }

    public EventPublishException(String eventId, String message, Throwable cause) {
        super(message, cause);
        this.eventId = eventId;
    }

    /**
     * 获取失败的事件 ID
     * 
     * @return 事件 ID，可能为 null
     */
    public String getEventId() {
        return eventId;
    }
}
