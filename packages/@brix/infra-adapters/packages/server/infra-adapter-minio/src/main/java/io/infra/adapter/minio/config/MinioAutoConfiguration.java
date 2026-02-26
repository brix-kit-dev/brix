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

import io.infra.adapter.minio.MinioFileStorageCapability;
import io.minio.MinioClient;
import io.runtime.sdk.capability.FileStorageCapability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * MinIO 文件存储适配器自动配置
 * 
 * <p>当 classpath 中存在 {@link MinioClient} 类且配置了 {@code brix.infra.minio.enabled=true}
 * 时，自动创建 MinIO 客户端和文件存储能力实例。</p>
 * 
 * <h3>激活条件</h3>
 * <ol>
 *   <li>classpath 中存在 {@code io.minio.MinioClient} 类</li>
 *   <li>配置属性 {@code brix.infra.minio.enabled} 为 {@code true}（默认）</li>
 *   <li>容器中不存在其他 {@link FileStorageCapability} 实例</li>
 * </ol>
 * 
 * <h3>蓝图对照</h3>
 * <p>对应蓝图 v3.0.2 Layer 2.5 适配器层，与 infra-adapter-kafka、infra-adapter-redis
 * 采用相同的自动配置模式。</p>
 * 
 * @author Brix Platform Authors
 * @since 3.0.0
 * @see MinioFileStorageCapability
 * @see MinioProperties
 */
@AutoConfiguration
@ConditionalOnClass(MinioClient.class)
@ConditionalOnProperty(prefix = "brix.infra.minio", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MinioProperties.class)
public class MinioAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MinioAutoConfiguration.class);

    /**
     * 创建 MinIO 客户端
     * 
     * <p>根据外部化配置属性构建 MinioClient 实例。
     * 如果配置了 region，则同时设置 region 参数。</p>
     * 
     * @param properties MinIO 配置属性
     * @return MinIO 客户端实例
     */
    @Bean
    @ConditionalOnMissingBean(MinioClient.class)
    public MinioClient minioClient(MinioProperties properties) {
        log.info("[MinIO] 初始化 MinIO 客户端: endpoint={}", properties.getEndpoint());

        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey());

        if (properties.getRegion() != null && !properties.getRegion().isEmpty()) {
            builder.region(properties.getRegion());
        }

        return builder.build();
    }

    /**
     * 创建文件存储能力实例
     * 
     * <p>基于 MinIO 客户端和默认 Bucket 名称创建 {@link FileStorageCapability} 实现。
     * 仅当容器中不存在其他 {@link FileStorageCapability} 时才创建。</p>
     * 
     * @param minioClient MinIO 客户端
     * @param properties  MinIO 配置属性
     * @return 文件存储能力实例
     */
    @Bean
    @ConditionalOnMissingBean(FileStorageCapability.class)
    public FileStorageCapability fileStorageCapability(MinioClient minioClient, MinioProperties properties) {
        log.info("[MinIO] 注册文件存储能力: bucket={}", properties.getBucketName());
        return new MinioFileStorageCapability(minioClient, properties.getBucketName());
    }
}
