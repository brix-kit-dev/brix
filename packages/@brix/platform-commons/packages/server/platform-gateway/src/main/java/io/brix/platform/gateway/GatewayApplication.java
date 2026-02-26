package io.brix.platform.gateway;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

/**
 * API 网关自动配置
 * <p>
 * v3.1: 从 @SpringBootApplication (含 main()) 改为 @AutoConfiguration。
 * main() 入口点属于 Host 层职责（ShellApplication），本类仅提供配置。
 * </p>
 * <p>
 * Gateway 不需要数据库，排除 JPA/DataSource 自动配置。
 * </p>
 *
 * @author Brix Platform Authors
 * @version 3.1.0
 */
@AutoConfiguration
@EnableAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class
})
public class GatewayApplication {
    // v3.1: main() 已移除 — Host 层 ShellApplication 为唯一启动入口
}
