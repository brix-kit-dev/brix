package io.brix.platform.auth.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限检查注
 * <p>
 * 标注在方法或类上，表示需要指定权限才能访问
 * </p>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    /**
     * 需要的权限列表
     * <p>
     * 使用 Immutable Permission ID 格式，如 "user:read", "order:create"
     * </p>
     *
     * @return 权限标识数组
     */
    String[] value();

    /**
     * 逻辑模式
     * <ul>
     *   <li>AND - 需要拥有所有权</li>
     *   <li>OR - 只需拥有任一权限</li>
     * </ul>
     *
     * @return 逻辑模式，默OR
     */
    Logical logical() default Logical.OR;

    /**
     * 逻辑模式枚举
     */
    enum Logical {
        AND, OR
    }
}
