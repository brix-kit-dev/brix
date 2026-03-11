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
package io.brix.platform.starter.registration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * Plugin Manifest Model
 * 
 * <p>Corresponds to META-INF/plugin-manifest.json file in plugin JAR</p>
 * 
 * <p>Each plugin can define a manifest file in its own JAR package, declaring:</p>
 * <ul>
 *   <li>Plugin basic info (name, version, description)</li>
 *   <li>UI contracts (routes, menu configuration)</li>
 *   <li>Event contracts (events published and subscribed)</li>
 * </ul>
 * 
 * <p>Example manifest file:</p>
 * <pre>
 * {
 *   "name": "plugin-user",
 *   "version": "1.0.0",
 *   "displayName": "User Management",
 *   "ui": {
 *     "web": {
 *       "routes": [
 *         {
 *           "path": "/users",
 *           "menu": {
 *             "title": "User List",
 *             "icon": "user",
 *             "order": 100
 *           }
 *         }
 *       ]
 *     }
 *   }
 * }
 * </pre>
 * 
 * @author Brix Platform Authors Platform Team
 * @since v2.1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PluginManifest {
    
    /** Plugin name (unique identifier) */
    private String name;
    
    /** Plugin version */
    private String version;
    
    /** Plugin display name */
    private String displayName;
    
    /** Plugin description */
    private String description;
    
    /** UI Contract */
    private UiContract ui;
    
    /** Event contract */
    private EventContract events;
    
    /** Extended metadata */
    private Map<String, Object> metadata;
    
    // ===== Getters and Setters =====
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public UiContract getUi() {
        return ui;
    }
    
    public void setUi(UiContract ui) {
        this.ui = ui;
    }
    
    public EventContract getEvents() {
        return events;
    }
    
    public void setEvents(EventContract events) {
        this.events = events;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    // ===== Nested Classes =====
    
    /**
     * UI Contract
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UiContract {
        
        private WebUi web;
        private MobileUi mobile;
        
        public WebUi getWeb() {
            return web;
        }
        
        public void setWeb(WebUi web) {
            this.web = web;
        }
        
        public MobileUi getMobile() {
            return mobile;
        }
        
        public void setMobile(MobileUi mobile) {
            this.mobile = mobile;
        }
    }
    
    /**
     * Web UI Configuration
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebUi {
        
        /** Remote entry URL (micro-frontend remoteEntry.js) */
        private String remoteEntry;
        
        /** Module Federation scope name */
        private String scope;
        
        /** Route list */
        private List<WebRoute> routes;
        
        public String getRemoteEntry() {
            return remoteEntry;
        }
        
        public void setRemoteEntry(String remoteEntry) {
            this.remoteEntry = remoteEntry;
        }
        
        public String getScope() {
            return scope;
        }
        
        public void setScope(String scope) {
            this.scope = scope;
        }
        
        public List<WebRoute> getRoutes() {
            return routes;
        }
        
        public void setRoutes(List<WebRoute> routes) {
            this.routes = routes;
        }
    }
    
    /**
     * Mobile UI Configuration
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MobileUi {
        
        /** Mobile page base URL */
        private String baseUrl;
        
        /** Route list */
        private List<WebRoute> routes;
        
        public String getBaseUrl() {
            return baseUrl;
        }
        
        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
        
        public List<WebRoute> getRoutes() {
            return routes;
        }
        
        public void setRoutes(List<WebRoute> routes) {
            this.routes = routes;
        }
    }
    
    /**
     * Web Route Configuration
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebRoute {
        
        /** Route path */
        private String path;
        
        /** Component name (micro-frontend remote component) */
        private String component;
        
        /** Whether to match exactly */
        private Boolean exact;
        
        /** Menu configuration */
        private Menu menu;
        
        public String getPath() {
            return path;
        }
        
        public void setPath(String path) {
            this.path = path;
        }
        
        public String getComponent() {
            return component;
        }
        
        public void setComponent(String component) {
            this.component = component;
        }
        
        public Boolean getExact() {
            return exact;
        }
        
        public void setExact(Boolean exact) {
            this.exact = exact;
        }
        
        public Menu getMenu() {
            return menu;
        }
        
        public void setMenu(Menu menu) {
            this.menu = menu;
        }
    }
    
    /**
     * Menu Configuration
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Menu {
        
        /** Menu title */
        private String title;
        
        /** Menu icon */
        private String icon;
        
        /** Menu order (smaller numbers appear first) */
        private Integer order;
        
        /** Parent menu ID (for nested menus) */
        private String parentId;
        
        /** Whether hidden (not displayed in menu but route is accessible) */
        private Boolean hidden;
        
        public String getTitle() {
            return title;
        }
        
        public void setTitle(String title) {
            this.title = title;
        }
        
        public String getIcon() {
            return icon;
        }
        
        public void setIcon(String icon) {
            this.icon = icon;
        }
        
        public Integer getOrder() {
            return order;
        }
        
        public void setOrder(Integer order) {
            this.order = order;
        }
        
        public String getParentId() {
            return parentId;
        }
        
        public void setParentId(String parentId) {
            this.parentId = parentId;
        }
        
        public Boolean getHidden() {
            return hidden;
        }
        
        public void setHidden(Boolean hidden) {
            this.hidden = hidden;
        }
    }
    
    /**
     * Event Contract
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EventContract {
        
        /** List of published events */
        private List<String> publish;
        
        /** List of subscribed events */
        private List<String> subscribe;
        
        public List<String> getPublish() {
            return publish;
        }
        
        public void setPublish(List<String> publish) {
            this.publish = publish;
        }
        
        public List<String> getSubscribe() {
            return subscribe;
        }
        
        public void setSubscribe(List<String> subscribe) {
            this.subscribe = subscribe;
        }
    }
}
