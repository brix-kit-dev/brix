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
 * 事件处理器注解
 * 
 * <p>用于标识一个方法为事件处理器。被此注解标注的方法将被 Runtime Shell 注册为事件监听器。</p>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Module(id = "booking-module", name = "预约模块")
 * public class BookingModule extends AbstractModule {
 *     
 *     // 处理用户创建事件
 *     @EventHandler
 *     public void onUserCreated(UserCreatedEvent event) {
 *         // 为新用户创建默认预约配置...
 *     }
 *     
 *     // 处理订单完成事件，指定事件类型
 *     @EventHandler(eventType = "com.example.OrderCompletedEvent")
 *     public void handleOrderCompleted(OrderCompletedEvent event) {
 *         // 处理订单完成后的预约更新...
 *     }
 *     
 *     // 异步处理大量事件
 *     @EventHandler(async = true)
 *     public void onBatchDataSync(DataSyncEvent event) {
 *         // 异步处理数据同步...
 *     }
 * }
 * }</pre>
 * 
 * <h3>方法签名要求</h3>
 * <ul>
 *   <li>必须是 public 方法</li>
 *   <li>必须有且仅有一个参数，参数类型为事件类</li>
 *   <li>返回值应为 void（非 void 返回值会被忽略）</li>
 * </ul>
 * 
 * <h3>与 Manifest 的关系</h3>
 * <p>注解声明的事件处理器需要在 module-manifest.yaml 的 subscribes 中声明白名单。
 * 只有在白名单中的事件类型才会被路由到对应的处理器。</p>
 * 
 * <pre>{@code
 * # module-manifest.yaml
 * events:
 *   subscribes:
 *     - type: "com.example.UserCreatedEvent"
 *       handler: "com.example.BookingModule.onUserCreated"
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EventHandler {

    /**
     * 事件类型
     * 
     * <p>默认从方法参数类型推断。如果指定，则使用指定的类型名。</p>
     * 
     * @return 事件类型全限定名
     */
    String eventType() default "";

    /**
     * 是否异步处理
     * 
     * <p>如果为 true，事件将在独立线程中异步处理，不阻塞事件发布者</p>
     * 
     * @return 是否异步处理，默认 false
     */
    boolean async() default false;

    /**
     * 处理顺序
     * 
     * <p>当多个处理器订阅同一事件时，按 order 从小到大顺序执行</p>
     * 
     * @return 处理顺序，默认 0
     */
    int order() default 0;

    /**
     * 条件表达式
     * 
     * <p>SpEL 表达式，只有表达式计算结果为 true 时才执行处理器</p>
     * <pre>{@code
     * @EventHandler(condition = "#event.amount > 1000")
     * public void onLargeOrder(OrderCreatedEvent event) { }
     * }</pre>
     * 
     * @return 条件表达式，默认为空（无条件）
     */
    String condition() default "";
}
