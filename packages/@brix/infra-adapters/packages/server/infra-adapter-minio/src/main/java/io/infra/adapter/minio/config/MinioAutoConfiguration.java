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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import io.infra.adapter.minio.MinioFileStorageCapability;
import io.minio.MinioClient;
import io.runtime.sdk.capability.FileStorageCapability;

/**
 * MinIO file storage adapter auto-configuration.
 * 
 * <p>Automatically creates MinIO client and file storage capability instance when
 * {@link MinioClient} class is present in classpath and {@code brix.infra.minio.enabled=true}
 * is configured.</p>
 * 
 * <h3>Activation Conditions</h3>
 * <ol>
 *   <li>{@code io.minio.MinioClient} class exists in classpath</li>
 *   <li>Configuration property {@code brix.infra.minio.enabled} is {@code true} (default)</li>
 *   <li>No other {@link FileStorageCapability} instance exists in the container</li>
 * </ol>
 * 
 * <h3>Architecture Compliance</h3>
 * <p>Auto-configuration for MinIO file storage adapter.
 * Follows the same auto-configuration pattern as infra-adapter-kafka and infra-adapter-redis.</p>
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
     * Creates MinIO client.
     * 
     * <p>Builds MinioClient instance based on externalized configuration properties.
     * If region is configured, it is also set.</p>
     * 
     * @param properties MinIO configuration properties
     * @return MinIO client instance
     */
    @Bean
    @ConditionalOnMissingBean(MinioClient.class)
    public MinioClient minioClient(MinioProperties properties) {
        log.info("[MinIO] Initializing MinIO client: endpoint={}", properties.getEndpoint());

        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey());

        if (properties.getRegion() != null && !properties.getRegion().isEmpty()) {
            builder.region(properties.getRegion());
        }

        return builder.build();
    }

    /**
     * Creates file storage capability instance.
     * 
     * <p>Creates {@link FileStorageCapability} implementation based on MinIO client and
     * default bucket name. Only created when no other {@link FileStorageCapability} exists
     * in the container.</p>
     *
     * <p><b>Return type rationale</b>: declared as the concrete
     * {@link MinioFileStorageCapability} (not the {@link FileStorageCapability}
     * interface) so Spring can resolve the bean's runtime type — and therefore
     * the {@code @Capability} annotation on it — <em>before</em> the bean is
     * instantiated. This lets {@code CapabilityAutoConfiguration} discover the
     * Adapter via {@code getBeanNamesForAnnotation(Capability.class)} and
     * register it into the {@code CapabilityRegistry} during the eager
     * registry-building phase, instead of after the registry has been frozen.
     * (Blueprint v3.0.x §3 Capability Contract; aligns with Spring Framework
     * guidance to declare {@code @Bean} methods with concrete return types
     * whenever annotation-driven introspection is required.)</p>
     * 
     * @param minioClient MinIO client
     * @param properties  MinIO configuration properties
     * @return File storage capability instance
     */
    @Bean
    @ConditionalOnMissingBean(FileStorageCapability.class)
    public MinioFileStorageCapability fileStorageCapability(MinioClient minioClient, MinioProperties properties) {
        log.info("[MinIO] Registering file storage capability: bucket={}", properties.getBucketName());
        return new MinioFileStorageCapability(minioClient, properties.getBucketName());
    }
}
