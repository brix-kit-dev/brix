/*
 * Copyright 2026 Brix Authors
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
package io.infra.adapter.minio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 文件存储适配器配置属性
 * 
 * <p>通过 Spring Boot 外部化配置管理 MinIO 连接信息。
 * 所有属性均通过 {@code brix.infra.minio.*} 前缀注入。</p>
 * 
 * <h3>配置示例</h3>
 * <pre>
 * brix:
 *   infra:
 *     minio:
 *       enabled: true
 *       endpoint: http://localhost:9000
 *       access-key: minioadmin
 *       secret-key: minioadmin
 *       bucket-name: default
 * </pre>
 * 
 * @author Brix Platform Authors
 * @since 3.0.0
 */
@ConfigurationProperties(prefix = "brix.infra.minio")
public class MinioProperties {

    /**
     * 是否启用 MinIO 适配器
     * 
     * <p>设置为 {@code false} 可禁用此适配器的自动配置。</p>
     */
    private boolean enabled = true;

    /**
     * MinIO 服务端点地址
     * 
     * <p>示例：{@code http://localhost:9000}</p>
     */
    private String endpoint;

    /**
     * MinIO 访问密钥（Access Key）
     */
    private String accessKey;

    /**
     * MinIO 秘密密钥（Secret Key）
     */
    private String secretKey;

    /**
     * 默认存储桶名称
     * 
     * <p>如果 Bucket 不存在，适配器会在初始化时自动创建。</p>
     */
    private String bucketName = "default";

    /**
     * MinIO 地域（Region）
     * 
     * <p>通常仅在使用 AWS S3 兼容模式时需要配置。</p>
     */
    private String region;

    // --- Getters & Setters ---

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }
}
