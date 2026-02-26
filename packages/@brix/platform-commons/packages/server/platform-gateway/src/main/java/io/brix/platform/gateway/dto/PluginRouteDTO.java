package io.brix.platform.gateway.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 插件路由 DTO
 * <p>
 * 用于反序列化 Redis 中存储的路由 JSON 数据
 * 作为 Gateway 模块Plugin Engine 之间路由信息传递的数据载体
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PluginRouteDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 路由唯一标识
     */
    private String id;

    /**
     * 匹配路径模式，例/api/users/**
     */
    private String path;

    /**
     * 目标服务 URI，例http://localhost:8082
     */
    private String targetUri;

    /**
     * 路由过滤器配置（可选）
     */
    private List<String> filters;

    /**
     * 是否需要认证（可选）
     */
    private Boolean authRequired;

    /**
     * 允许访问的角色列表（可选）
     */
    private List<String> roles;

    /**
     * 默认构造函数
     */
    public PluginRouteDTO() {
    }

    /**
     * 全参构造函数
     *
     * @param id        路由 ID
     * @param path      匹配路径
     * @param targetUri 目标 URI
     */
    public PluginRouteDTO(String id, String path, String targetUri) {
        this.id = id;
        this.path = path;
        this.targetUri = targetUri;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getTargetUri() {
        return targetUri;
    }

    public void setTargetUri(String targetUri) {
        this.targetUri = targetUri;
    }

    public List<String> getFilters() {
        return filters;
    }

    public void setFilters(List<String> filters) {
        this.filters = filters;
    }

    public Boolean getAuthRequired() {
        return authRequired;
    }

    public void setAuthRequired(Boolean authRequired) {
        this.authRequired = authRequired;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PluginRouteDTO that = (PluginRouteDTO) o;
        return Objects.equals(id, that.id) &&
               Objects.equals(path, that.path) &&
               Objects.equals(targetUri, that.targetUri) &&
               Objects.equals(filters, that.filters) &&
               Objects.equals(authRequired, that.authRequired) &&
               Objects.equals(roles, that.roles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, path, targetUri, filters, authRequired, roles);
    }

    @Override
    public String toString() {
        return "PluginRouteDTO{" +
                "id='" + id + '\'' +
                ", path='" + path + '\'' +
                ", targetUri='" + targetUri + '\'' +
                ", filters=" + filters +
                ", authRequired=" + authRequired +
                ", roles=" + roles +
                '}';
    }
}
