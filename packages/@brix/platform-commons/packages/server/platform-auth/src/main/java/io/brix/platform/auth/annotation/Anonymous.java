package io.brix.platform.auth.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 匿名访问注解
 * <p>
 * 标注在方法或类上，表示该接口允许匿名访问，不需要认证
 * 主要用于登录、注册、公开 API 等场景
 * </p>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Anonymous {
}
