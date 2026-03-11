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
 * State store deserialization exception.
 * 
 * <p>Thrown when data read from Redis cannot be deserialized to the target type.
 * Callers can use this to distinguish between "key does not exist" (returns Optional.empty())
 * and "value is corrupted" (throws this exception).</p>
 * 
 * @author Brix Platform Authors
 * @since 3.0.0
 * @see RedisStateStoreCapability
 */
public class StateDeserializationException extends StateStoreException {

    /**
     * The key that caused deserialization failure.
     */
    private final String key;

    /**
     * The target type.
     */
    private final String targetType;

    public StateDeserializationException(String key, String targetType, String message, Throwable cause) {
        super(message, cause);
        this.key = key;
        this.targetType = targetType;
    }

    /**
     * Gets the key that caused deserialization failure.
     * 
     * @return the storage key
     */
    public String getKey() {
        return key;
    }

    /**
     * Gets the target type name.
     * 
     * @return the target type
     */
    public String getTargetType() {
        return targetType;
    }
}
