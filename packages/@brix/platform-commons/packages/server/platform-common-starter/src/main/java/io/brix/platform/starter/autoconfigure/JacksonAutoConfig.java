package io.brix.platform.starter.autoconfigure;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * v2.1 Jackson 自动配置
 * 
 * <p>提供统一JSON 序列化配置：</p>
 * <ul>
 *   <li>Java 8 日期时间支持（JSR-310</li>
 *   <li>日期时间格式化为 ISO-8601</li>
 *   <li>蹇界暐鏈煡灞炴€э紙鍏煎鎬э級</li>
 *   <li>空对象不报错</li>
 * </ul>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 */
@AutoConfiguration(before = JacksonAutoConfiguration.class)
@ConditionalOnClass(ObjectMapper.class)
public class JacksonAutoConfig {
    
    /**
     * 配置 ObjectMapper
     * 
     * <p>统一 JSON 序列化配置，确保所有服务行为一</p>
     * 
     * @param builder Jackson 构建
     * @return 配置好的 ObjectMapper
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objectMapper = builder.createXmlMapper(false).build();
        
        // 注册 Java 8 日期时间模块
        objectMapper.registerModule(new JavaTimeModule());
        
        // 日期时间序列化为 ISO-8601 格式而非时间
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // 忽略未知属性，提高兼容
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        
        // 空对象不报错
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        
        return objectMapper;
    }
}
