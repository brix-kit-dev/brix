package io.brix.platform.auth.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色检查注
 * <p>
 * 标注在方法或类上，表示需要指定角色才能访问
 * </p>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    /**
     * 需要的角色列表
     *
     * @return 角色名称数组
     */
    String[] value();

    /**
     * 逻辑模式
     * <ul>
     *   <li>AND - 需要拥有所有角</li>
     *   <li>OR - 只需拥有任一角色</li>
     * </ul>
     *
     * @return 逻辑模式，默OR
     */
    RequirePermission.Logical logical() default RequirePermission.Logical.OR;
}
