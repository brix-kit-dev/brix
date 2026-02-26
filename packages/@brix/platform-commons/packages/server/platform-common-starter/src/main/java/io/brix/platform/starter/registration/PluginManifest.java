package io.brix.platform.starter.registration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * 插件 Manifest 模型
 * 
 * <p>对应插件 JAR 中的 META-INF/plugin-manifest.json 文件</p>
 * 
 * <p>每个插件可以在自己的 JAR 包中定义 manifest 文件，声明：</p>
 * <ul>
 *   <li>插件基本信息（名称、版本、描述）</li>
 *   <li>UI 契约（路由、菜单配置）</li>
 *   <li>事件契约（发订阅的事件）</li>
 * </ul>
 * 
 * <p>示例 manifest 文件</p>
 * <pre>
 * {
 *   "name": "plugin-user",
 *   "version": "1.0.0",
 *   "displayName": "用户管理",
 *   "ui": {
 *     "web": {
 *       "routes": [
 *         {
 *           "path": "/users",
 *           "menu": {
 *             "title": "用户列表",
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
    
    /** 插件名称（唯一标识*/
    private String name;
    
    /** 插件版本 */
    private String version;
    
    /** 插件显示名称 */
    private String displayName;
    
    /** 插件描述 */
    private String description;
    
    /** UI 濂戠害 */
    private UiContract ui;
    
    /** 事件契约 */
    private EventContract events;
    
    /** 扩展元数*/
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
    
    // ===== 嵌套=====
    
    /**
     * UI 濂戠害
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
     * Web UI 配置
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebUi {
        
        /** 远程入口地址（微前端 remoteEntry.js*/
        private String remoteEntry;
        
        /** 模块联邦 scope 名称 */
        private String scope;
        
        /** 路由列表 */
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
     * Mobile UI 配置
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MobileUi {
        
        /** 移动端页URL */
        private String baseUrl;
        
        /** 路由列表 */
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
     * Web 路由配置
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebRoute {
        
        /** 路由路径 */
        private String path;
        
        /** 组件名称（微前端远程组件*/
        private String component;
        
        /** 是否精确匹配 */
        private Boolean exact;
        
        /** 菜单配置 */
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
     * 菜单配置
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Menu {
        
        /** 菜单标题 */
        private String title;
        
        /** 菜单图标 */
        private String icon;
        
        /** 菜单排序（数字越小越靠前*/
        private Integer order;
        
        /** 父菜ID（用于嵌套菜单） */
        private String parentId;
        
        /** 是否隐藏（不在菜单中显示，但路由可访问） */
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
     * 事件契约
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EventContract {
        
        /** 发布的事件列*/
        private List<String> publish;
        
        /** 订阅的事件列*/
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
