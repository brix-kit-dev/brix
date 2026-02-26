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
package io.runtime.sdk.host;

/**
 * Host 类型枚举
 * 
 * <p>定义不同类型的 Host 实现，每种类型有不同的能力支持和适用场景。</p>
 * 
 * <h3>类型说明</h3>
 * <table border="1">
 *   <tr><th>类型</th><th>能力级别</th><th>典型用途</th></tr>
 *   <tr><td>FULL_PRODUCT</td><td>完整</td><td>独立部署的完整产品</td></tr>
 *   <tr><td>EMBEDDED</td><td>精简</td><td>嵌入客户系统</td></tr>
 *   <tr><td>STANDALONE</td><td>最小</td><td>单机开发测试</td></tr>
 *   <tr><td>TEST</td><td>Mock</td><td>单元测试</td></tr>
 * </table>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see Host#getType()
 */
public enum HostType {

    /**
     * 完整产品 Host
     * 
     * <p>提供所有能力的完整实现，包括：</p>
     * <ul>
     *   <li>Kafka 事件总线</li>
     *   <li>Redis 状态存储</li>
     *   <li>完整的可观测性（OpenTelemetry）</li>
     *   <li>JWT 认证</li>
     *   <li>所有可选能力</li>
     * </ul>
     */
    FULL_PRODUCT("full-product", "完整产品模式"),

    /**
     * 嵌入式 Host
     * 
     * <p>精简的能力实现，适合嵌入客户系统：</p>
     * <ul>
     *   <li>HTTP Webhook 事件总线</li>
     *   <li>本地内存状态存储</li>
     *   <li>委托认证（Delegated Auth）</li>
     *   <li>基础可观测性</li>
     * </ul>
     */
    EMBEDDED("embedded", "嵌入模式"),

    /**
     * 单机 Host
     * 
     * <p>最小化实现，用于本地开发：</p>
     * <ul>
     *   <li>内存事件总线</li>
     *   <li>内存状态存储</li>
     *   <li>简单认证</li>
     *   <li>控制台日志</li>
     * </ul>
     */
    STANDALONE("standalone", "单机模式"),

    /**
     * 测试 Host
     * 
     * <p>Mock 实现，用于单元测试：</p>
     * <ul>
     *   <li>所有能力为 Mock 实现</li>
     *   <li>支持断言和验证</li>
     *   <li>可配置行为</li>
     * </ul>
     */
    TEST("test", "测试模式");

    /**
     * 类型标识
     */
    private final String code;

    /**
     * 类型描述
     */
    private final String description;

    HostType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 获取类型标识
     * 
     * @return 类型标识
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取类型描述
     * 
     * @return 类型描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 根据标识查找类型
     * 
     * @param code 类型标识
     * @return 对应的 HostType，如果未找到返回 null
     */
    public static HostType fromCode(String code) {
        for (HostType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
