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
 * MinIO file storage adapter configuration properties.
 * 
 * <p>Manages MinIO connection information through Spring Boot externalized configuration.
 * All properties are injected via the {@code brix.infra.minio.*} prefix.</p>
 * 
 * <h3>Configuration Example</h3>
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
     * Whether MinIO adapter is enabled.
     * 
     * <p>Set to {@code false} to disable auto-configuration of this adapter.</p>
     */
    private boolean enabled = true;

    /**
     * MinIO server endpoint address.
     * 
     * <p>Example: {@code http://localhost:9000}</p>
     */
    private String endpoint;

    /**
     * MinIO access key.
     */
    private String accessKey;

    /**
     * MinIO secret key.
     */
    private String secretKey;

    /**
     * Default bucket name.
     * 
     * <p>If the bucket does not exist, the adapter will automatically create it during initialization.</p>
     */
    private String bucketName = "default";

    /**
     * MinIO region.
     * 
     * <p>Usually only needs to be configured when using AWS S3 compatible mode.</p>
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
