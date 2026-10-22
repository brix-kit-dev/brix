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
package io.brix.platform.common.util;

import java.util.Objects;

import org.springframework.lang.NonNull;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * <p>Jackson serialization/deserialization utility, unified ObjectMapper configuration,
 * avoiding duplicate creation across modules.</p>
 * <p>This utility class is thread-safe and can be directly reused in multi-threaded environments.</p>
 */
public final class JsonUtils {
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .configureForJackson2()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private JsonUtils() {
    }

    /**
     * Serialize an object to JSON string.
     *
     * @param value Any object
     * @return JSON string
     */
    public static @NonNull String toJson(Object value) {
        try {
            return Objects.requireNonNull(MAPPER.writeValueAsString(value), "JSON serialization returned null");
        } catch (JacksonException e) {
            throw new IllegalStateException("JSON serialization failed", e);
        }
    }

    /**
     * Deserialize JSON string to specified type.
     *
     * @param json  JSON string
     * @param clazz Target type
     * @param <T>   Generic type
     * @return Target object
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JacksonException e) {
            throw new IllegalStateException("JSON deserialization failed", e);
        }
    }

    /**
     * TypeReference-based deserialization utility, suitable for collections or complex generic types.
     */
    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        try {
            return MAPPER.readValue(json, typeReference);
        } catch (JacksonException e) {
            throw new IllegalStateException("JSON deserialization failed", e);
        }
    }

    /**
     * Parse JSON string to {@link JsonNode} for dynamic reading.
     */
    public static JsonNode readTree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JacksonException e) {
            throw new IllegalStateException("JSON parsing failed", e);
        }
    }

    /**
     * Expose underlying ObjectMapper for business scenarios requiring custom behavior.
     */
    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
