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
package io.runtime.sdk.capability;

/**
 * 日志级别枚举
 * 
 * <p>定义可观测性日志的级别，从低到高排序。</p>
 * 
 * <h3>级别说明</h3>
 * <ul>
 *   <li><b>TRACE</b>：最详细的调试信息，通常只在开发环境启用</li>
 *   <li><b>DEBUG</b>：调试信息，用于问题排查</li>
 *   <li><b>INFO</b>：重要的业务事件，如用户登录、订单创建</li>
 *   <li><b>WARN</b>：警告信息，如配置缺失、性能下降</li>
 *   <li><b>ERROR</b>：错误信息，需要关注但不影响系统运行</li>
 * </ul>
 * 
 * <h3>级别选择建议</h3>
 * <table border="1">
 *   <tr><th>场景</th><th>推荐级别</th></tr>
 *   <tr><td>方法入口/出口追踪</td><td>TRACE</td></tr>
 *   <tr><td>变量值、条件分支</td><td>DEBUG</td></tr>
 *   <tr><td>业务事件（登录、下单）</td><td>INFO</td></tr>
 *   <tr><td>可恢复的异常</td><td>WARN</td></tr>
 *   <tr><td>不可恢复的错误</td><td>ERROR</td></tr>
 * </table>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see ObservabilityCapability#log(LogLevel, String, Object...)
 */
public enum LogLevel {

    /**
     * 追踪级别 - 最详细的调试信息
     */
    TRACE(0),

    /**
     * 调试级别 - 用于开发调试
     */
    DEBUG(1),

    /**
     * 信息级别 - 重要业务事件
     */
    INFO(2),

    /**
     * 警告级别 - 潜在问题
     */
    WARN(3),

    /**
     * 错误级别 - 需要关注的错误
     */
    ERROR(4);

    /**
     * 级别序号，用于级别比较
     */
    private final int level;

    LogLevel(int level) {
        this.level = level;
    }

    /**
     * 获取级别序号
     * 
     * @return 级别序号
     */
    public int getLevel() {
        return level;
    }

    /**
     * 判断当前级别是否高于或等于指定级别
     * 
     * @param other 要比较的级别
     * @return 如果当前级别 >= 指定级别返回 true
     */
    public boolean isEnabledFor(LogLevel other) {
        return this.level >= other.level;
    }
}
