package io.brix.platform.observability.tracing;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

import io.brix.platform.observability.ObservabilityProperties;

/**
 * 链路追踪自动配置
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 */
@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "observability.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TracingAutoConfiguration {

    @Bean
    public TraceContextHolder traceContextHolder() {
        return new TraceContextHolder();
    }

    @Bean
    public TraceIdGenerator traceIdGenerator() {
        return new TraceIdGenerator();
    }

    @Bean
    public FilterRegistrationBean<TracePropagationFilter> tracePropagationFilter(
            ObservabilityProperties properties,
            TraceContextHolder traceContextHolder,
            TraceIdGenerator traceIdGenerator) {
        
        TracePropagationFilter filter = new TracePropagationFilter(
            properties.getTracing(),
            traceContextHolder,
            traceIdGenerator
        );
        
        FilterRegistrationBean<TracePropagationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setName("tracePropagationFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        
        return registration;
    }
}
