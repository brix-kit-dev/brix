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
package io.runtime.manifest.model;

/**
 * Menu Configuration.
 *
 * <p>Configures menu items contributed by a module including id, title, icon, and hierarchy.</p>
 * <p>【菜单配置】配置模块贡献的菜单项，包括标识、标题、图标和层级关系。</p>
 *
 * <p>Extracted from ModuleManifest.java as part of v3.2 architecture refactoring
 * to keep each file under 500 lines per code quality guidelines.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see ResourceConfig
 */
public class MenuConfig {

    /**
     * Menu ID.
     * 菜单 ID
     */
    private String id;

    /**
     * Menu title.
     * 菜单标题
     */
    private String title;

    /**
     * Icon.
     * 图标
     */
    private String icon;

    /**
     * Parent menu ID.
     * 父菜单 ID
     */
    private String parentId;

    /**
     * Sort order.
     * 排序
     */
    private int order = 0;

    /**
     * Route path.
     * 路由路径
     */
    private String route;

    // ==================== Getters and Setters ====================

    public String getId() { 
        return id; 
    }
    
    public void setId(String id) { 
        this.id = id; 
    }
    
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
    
    public String getParentId() { 
        return parentId; 
    }
    
    public void setParentId(String parentId) { 
        this.parentId = parentId; 
    }
    
    public int getOrder() { 
        return order; 
    }
    
    public void setOrder(int order) { 
        this.order = order; 
    }
    
    public String getRoute() { 
        return route; 
    }
    
    public void setRoute(String route) { 
        this.route = route; 
    }

    @Override
    public String toString() {
        return "MenuConfig{" +
               "id='" + id + '\'' +
               ", title='" + title + '\'' +
               ", route='" + route + '\'' +
               '}';
    }
}
