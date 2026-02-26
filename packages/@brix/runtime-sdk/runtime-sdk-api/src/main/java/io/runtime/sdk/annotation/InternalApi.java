/*
 * Copyright 2026 Runtime SDK Authors
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
package io.runtime.sdk.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 内部 API 标记注解
 * 
 * <p>标记为内部 API 的方法、类或接口仅供运行时框架内部使用，
 * <b>插件/模块不应直接调用</b>。内部 API 可能在小版本升级时发生
 * 不兼容变更，无需遵循语义化版本的兼容性约束。</p>
 * 
 * <h3>使用场景</h3>
 * <ul>
 *   <li><b>基础设施暴露</b>：如 {@code DataSource}、{@code Connection} 等底层对象</li>
 *   <li><b>框架扩展点</b>：供 Host 适配器实现，不面向业务插件</li>
 *   <li><b>性能热路径</b>：为高性能场景提供的底层 API</li>
 * </ul>
 * 
 * <h3>ArchUnit 规则</h3>
 * <p>通过 architecture-guard 模块的 ArchUnit 规则检测：</p>
 * <pre>{@code
 * // 禁止插件直接调用 @InternalApi 标记的方法
 * noClasses().that().resideInAPackage("..module..")
 *     .should().callMethodsAnnotatedWith(InternalApi.class)
 *     .because("内部 API 仅供框架使用，插件应使用公开 API");
 * }</pre>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * public interface DatabaseCapability {
 *     
 *     // 推荐：插件使用此方法执行 SQL
 *     <T> T executeNative(String sql, Class<T> resultType, Object... params);
 *     
 *     // 内部 API：仅供适配器层使用
 *     @InternalApi("暴露基础设施类型，插件应使用 executeNative()")
 *     DataSource getDataSource();
 * }
 * }</pre>
 * 
 * <h3>与 @Deprecated 的区别</h3>
 * <ul>
 *   <li>{@code @Deprecated}：API 将被移除，请迁移到替代方案</li>
 *   <li>{@code @InternalApi}：API 持续存在，但不面向插件开发者</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.2.0
 * @see io.runtime.sdk.capability.DatabaseCapability#getDataSource()
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface InternalApi {
    
    /**
     * 说明此 API 为内部 API 的原因以及推荐的替代方案
     * 
     * @return 内部 API 说明
     */
    String value() default "";
    
    /**
     * 推荐使用的公开 API 方法名
     * 
     * <p>如果存在替代的公开 API，在此指定方法名以便 IDE 提示。</p>
     * 
     * @return 推荐的替代方法名，空字符串表示无替代
     */
    String instead() default "";
}
