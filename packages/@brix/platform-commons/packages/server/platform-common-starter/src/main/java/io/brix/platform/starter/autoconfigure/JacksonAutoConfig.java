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
package io.brix.platform.starter.autoconfigure;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * v2.1 Jackson Auto-Configuration
 * 
 * <p>Provides unified JSON serialization configuration:</p>
 * <ul>
 *   <li>Java 8 date/time support (JSR-310)</li>
 *   <li>Date/time formatted as ISO-8601</li>
 *   <li>Ignore unknown properties (compatibility)</li>
 * </ul>
 * 
 * <p>Spring Boot 4 auto-configures a Jackson 3 {@link JsonMapper}; this configuration customizes
 * that mapper so the JSON contract stays identical to the Spring Boot 3 / Jackson 2 mapper that
 * the hosts actually used (Spring Boot's own {@code jacksonObjectMapper}: Jackson 2 defaults,
 * ISO-8601 dates, unknown properties ignored, JDK 8 / java.time / parameter-names modules).</p>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 */
@AutoConfiguration(before = JacksonAutoConfiguration.class)
@ConditionalOnClass(JsonMapper.class)
public class JacksonAutoConfig {
    
    /**
     * Configure the auto-configured JsonMapper
     * 
     * <p>Unified JSON serialization configuration ensuring consistent behavior across all services.
     * It starts from the Jackson 2 defaults the Spring Boot 3 mapper was built on and runs before
     * Spring Boot's own customizer, so explicit {@code spring.jackson.*} properties still win.</p>
     * 
     * <p>Empty beans keep failing serialization: under Spring Boot 3 the auto-configured
     * {@code jacksonObjectMapper} always took precedence over the mapper this class used to
     * declare, so {@code FAIL_ON_EMPTY_BEANS} stayed at the Jackson 2 default.</p>
     * 
     * @return JsonMapper builder customizer
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public JsonMapperBuilderCustomizer brixJsonMapperBuilderCustomizer() {
        return builder -> builder
                // Jackson 2 defaults (property order, enum names, lenient parsing, ...)
                .configureForJackson2()
                // Spring Boot 3 registered the parameter-names module and disabled default view inclusion
                .enable(MapperFeature.DETECT_PARAMETER_NAMES)
                .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                // Serialize date/time and durations as ISO-8601 instead of timestamps
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                // Ignore unknown properties for better compatibility
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
