package io.brix.platform.common.util;

import java.io.IOException;
import java.util.Objects;

import org.springframework.lang.NonNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * <p>Jackson 序列化/反序列化工具，统一配置 ObjectMapper，避免各模块重复创建。</p>
 * <p>该工具类线程安全，可直接在多线程环境中复用。</p>
 */
public final class JsonUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JsonUtils() {
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param value 任意对象
     * @return JSON 字符串
     */
    public static @NonNull String toJson(Object value) {
        try {
            return Objects.requireNonNull(MAPPER.writeValueAsString(value), "JSON 序列化返回 null");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    /**
     * 将 JSON 字符串反序列化为指定类型。
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型
     * @param <T>   泛型
     * @return 目标对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (IOException e) {
            throw new IllegalStateException("JSON 反序列化失败", e);
        }
    }

    /**
     * 基于 TypeReference 的反序列化工具，适用于集合或泛型复杂类型。
     */
    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        try {
            return MAPPER.readValue(json, typeReference);
        } catch (IOException e) {
            throw new IllegalStateException("JSON 反序列化失败", e);
        }
    }

    /**
     * 解析 JSON 字符串为 {@link JsonNode}，便于上层进行动态读取。
     */
    public static JsonNode readTree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (IOException e) {
            throw new IllegalStateException("JSON 解析失败", e);
        }
    }

    /**
     * 暴露底层 ObjectMapper，供必须自定义行为的业务场景使用。
     */
    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
