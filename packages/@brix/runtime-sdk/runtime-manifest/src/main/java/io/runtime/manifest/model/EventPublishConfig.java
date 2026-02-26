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
 * Event Publish Configuration.
 *
 * <p>Declares event types that the module can publish along with their metadata.</p>
 * <p>【事件发布配置】声明模块可以发布的事件类型及其元数据。</p>
 *
 * <p>Extracted from ModuleManifest.java as part of v3.2 architecture refactoring
 * to keep each file under 500 lines per code quality guidelines.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see EventsConfig
 */
public class EventPublishConfig {

    /**
     * Event type (fully qualified class name).
     * 事件类型（完整类名）
     */
    private String type;

    /**
     * JSON Schema path.
     * JSON Schema 路径
     */
    private String schema;

    /**
     * Event description.
     * 事件描述
     */
    private String description;

    /**
     * Maximum payload size.
     * 最大消息体大小
     */
    private String maxPayloadSize = "1MB";

    /**
     * Serialization format.
     * 序列化格式
     */
    private String serialization = "JSON";

    // ==================== Getters and Setters ====================

    public String getType() { 
        return type; 
    }
    
    public void setType(String type) { 
        this.type = type; 
    }
    
    public String getSchema() { 
        return schema; 
    }
    
    public void setSchema(String schema) { 
        this.schema = schema; 
    }
    
    public String getDescription() { 
        return description; 
    }
    
    public void setDescription(String description) { 
        this.description = description; 
    }
    
    public String getMaxPayloadSize() { 
        return maxPayloadSize; 
    }
    
    public void setMaxPayloadSize(String maxPayloadSize) { 
        this.maxPayloadSize = maxPayloadSize; 
    }
    
    public String getSerialization() { 
        return serialization; 
    }
    
    public void setSerialization(String serialization) { 
        this.serialization = serialization; 
    }

    @Override
    public String toString() {
        return "EventPublishConfig{type='" + type + "'}";
    }
}
