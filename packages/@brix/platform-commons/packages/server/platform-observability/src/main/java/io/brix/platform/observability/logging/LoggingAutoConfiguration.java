package io.brix.platform.observability.logging;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 日志自动配置
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "observability.logging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LoggingAutoConfiguration {

    // 日志配置主要通过 logback-spring.xml 实现
    // 此类提供编程式配置入
}
