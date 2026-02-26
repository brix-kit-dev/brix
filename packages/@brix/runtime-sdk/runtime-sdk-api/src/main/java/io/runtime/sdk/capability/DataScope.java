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

import java.io.Serializable;
import java.util.Objects;

/**
 * 数据权限范围
 * 
 * <p>定义用户可访问的数据边界，用于行级数据权限控制。
 * DataScope 是一个不可变对象，包含范围类型和范围值。</p>
 * 
 * <h3>常见范围类型</h3>
 * <ul>
 *   <li><b>DEPARTMENT</b>：部门范围</li>
 *   <li><b>ORGANIZATION</b>：组织范围</li>
 *   <li><b>REGION</b>：地区范围</li>
 *   <li><b>SELF</b>：仅自己的数据</li>
 *   <li><b>ALL</b>：所有数据</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 在服务层使用数据范围过滤
 * Set<DataScope> scopes = authContext.getAuthorizedScopes();
 * 
 * for (DataScope scope : scopes) {
 *     if ("DEPARTMENT".equals(scope.getType())) {
 *         // 添加部门过滤条件
 *         query.where("department_id", scope.getValue());
 *     }
 * }
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see AuthContextCapability#getAuthorizedScopes()
 */
public final class DataScope implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 范围类型
     * 
     * <p>如 "DEPARTMENT", "ORGANIZATION", "REGION"</p>
     */
    private final String type;

    /**
     * 范围值
     * 
     * <p>具体的范围标识，如部门 ID、组织 ID、地区编码</p>
     */
    private final String value;

    /**
     * 创建数据权限范围
     * 
     * @param type  范围类型，不能为空
     * @param value 范围值，不能为空
     * @throws IllegalArgumentException 如果参数为空
     */
    public DataScope(String type, String value) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("DataScope type cannot be null or blank");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("DataScope value cannot be null or blank");
        }
        this.type = type;
        this.value = value;
    }

    /**
     * 获取范围类型
     * 
     * @return 范围类型
     */
    public String getType() {
        return type;
    }

    /**
     * 获取范围值
     * 
     * @return 范围值
     */
    public String getValue() {
        return value;
    }

    /**
     * 创建部门范围
     * 
     * @param departmentId 部门 ID
     * @return DataScope 实例
     */
    public static DataScope department(String departmentId) {
        return new DataScope("DEPARTMENT", departmentId);
    }

    /**
     * 创建组织范围
     * 
     * @param organizationId 组织 ID
     * @return DataScope 实例
     */
    public static DataScope organization(String organizationId) {
        return new DataScope("ORGANIZATION", organizationId);
    }

    /**
     * 创建地区范围
     * 
     * @param regionCode 地区编码
     * @return DataScope 实例
     */
    public static DataScope region(String regionCode) {
        return new DataScope("REGION", regionCode);
    }

    /**
     * 创建自身数据范围
     * 
     * @param userId 用户 ID
     * @return DataScope 实例
     */
    public static DataScope self(String userId) {
        return new DataScope("SELF", userId);
    }

    /**
     * 创建全部数据范围
     * 
     * @return DataScope 实例
     */
    public static DataScope all() {
        return new DataScope("ALL", "*");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataScope dataScope = (DataScope) o;
        return Objects.equals(type, dataScope.type) && Objects.equals(value, dataScope.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }

    @Override
    public String toString() {
        return String.format("DataScope[type=%s, value=%s]", type, value);
    }
}
