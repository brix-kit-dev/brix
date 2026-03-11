/*
 * Copyright 2026 Brix Platform Authors
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
package io.brix.platform.gateway.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Plugin Route DTO
 * <p>
 * Used to deserialize route JSON data stored in Redis
 * workis Gateway modulePlugin Engine betweenrouteinformationpassofcountdatacarrier
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PluginRouteDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Route unique identifier
     */
    private String id;

    /**
     * match pathpattern，example/api/users/**
     */
    private String path;

    /**
     * targetservice URI，examplehttp://localhost:8082
     */
    private String targetUri;

    /**
     * Route filter configuration (optional)
     */
    private List<String> filters;

    /**
     * Whether authentication is required (optional)
     */
    private Boolean authRequired;

    /**
     * List of allowed access roles (optional)
     */
    private List<String> roles;

    /**
     * Default constructor
     */
    public PluginRouteDTO() {
    }

    /**
     * Full parameter constructor
     *
     * @param id        route ID
     * @param path      match path
     * @param targetUri target URI
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
