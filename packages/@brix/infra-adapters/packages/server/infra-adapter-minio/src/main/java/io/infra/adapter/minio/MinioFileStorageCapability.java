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
package io.infra.adapter.minio;

import java.io.InputStream;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import io.runtime.sdk.capability.FileStorageCapability;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;

/**
 * MinIO-based file storage capability implementation.
 * 
 * <p>This class is the production implementation of {@link FileStorageCapability},
 * providing distributed object storage capability based on MinIO (S3-compatible API).
 * Plugins use this implementation to upload, download, and manage files without
 * needing to know about the MinIO SDK.</p>
 * 
 * <h3>Core Features</h3>
 * <ul>
 *   <li><b>S3 Compatible</b>: Based on MinIO SDK, compatible with all S3 API storage</li>
 *   <li><b>Auto Bucket Management</b>: Automatically creates bucket on first use</li>
 *   <li><b>Signed URLs</b>: Supports pre-signed download and upload URLs</li>
 *   <li><b>File Metadata</b>: Supports file size queries, existence checks</li>
 * </ul>
 * 
 * <h3>Architecture Compliance</h3>
 * <p>Abstracts MinIO access from plugin layer to adapter layer (Layer 2.5),
 * plugins access via capability contract.
 * Follows existing adapter patterns: {@code infra-adapter-kafka}, {@code infra-adapter-redis}.</p>
 * 
 * <h3>Thread Safety</h3>
 * <p>This class is thread-safe. MinioClient supports concurrent access.</p>
 * 
 * @author Brix Platform Authors
 * @since 3.0.0
 * @see FileStorageCapability
 */
@Capability(
    type = FileStorageCapability.class,
    name = "minio-file-storage",
    description = "MinIO (S3-compatible) file storage capability implementation",
    level = CapabilityLevel.STANDARD,
    aliases = {"fileStorage", "minioFileStorage"}
)
public class MinioFileStorageCapability implements FileStorageCapability {

    private static final Logger log = LoggerFactory.getLogger(MinioFileStorageCapability.class);

    /**
     * MinIO client.
     * 
     * <p>Created by auto-configuration based on external configuration,
     * encapsulates S3-compatible API calls.</p>
     */
    private final MinioClient minioClient;

    /**
     * Default bucket name.
     */
    private final String bucketName;

    /**
     * Constructor.
     * 
     * @param minioClient MinIO client instance
     * @param bucketName  Default bucket name
     */
    public MinioFileStorageCapability(MinioClient minioClient, String bucketName) {
        this.minioClient = Objects.requireNonNull(minioClient, "minioClient cannot be null");
        this.bucketName = Objects.requireNonNull(bucketName, "bucketName cannot be null");

        // Ensure bucket exists
        ensureBucketExists();

        log.info("[MinIO] File storage capability initialized: bucket={}", bucketName);
    }

    /**
     * Ensures bucket exists.
     * 
     * <p>Automatically creates the target bucket if it does not exist. This operation
     * is executed only once during adapter initialization.</p>
     */
    private void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("[MinIO] Created bucket: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("[MinIO] Failed to check/create bucket: bucket={}", bucketName, e);
            throw new RuntimeException("MinIO initialization failed: " + e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Uploads a file to the specified MinIO path with automatic Content-Type setting.</p>
     */
    @Override
    public String upload(String storagePath, InputStream inputStream, String contentType, long fileSize) {
        Objects.requireNonNull(storagePath, "storagePath cannot be null");
        Objects.requireNonNull(inputStream, "inputStream cannot be null");

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(storagePath)
                            .stream(inputStream, fileSize, -1)
                            .contentType(contentType)
                            .build());

            log.info("[MinIO] File uploaded successfully: bucket={}, path={}, size={}", bucketName, storagePath, fileSize);
            return storagePath;
        } catch (Exception e) {
            log.error("[MinIO] File upload failed: path={}", storagePath, e);
            throw new RuntimeException("File upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStream download(String storagePath) {
        Objects.requireNonNull(storagePath, "storagePath cannot be null");

        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(storagePath)
                            .build());
        } catch (Exception e) {
            log.error("[MinIO] File download failed: path={}", storagePath, e);
            throw new RuntimeException("File download failed: " + e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(String storagePath) {
        Objects.requireNonNull(storagePath, "storagePath cannot be null");

        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(storagePath)
                            .build());
            log.info("[MinIO] File deleted successfully: path={}", storagePath);
        } catch (Exception e) {
            log.error("[MinIO] File deletion failed: path={}", storagePath, e);
            throw new RuntimeException("File deletion failed: " + e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean exists(String storagePath) {
        Objects.requireNonNull(storagePath, "storagePath cannot be null");

        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(storagePath)
                            .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Uses MinIO pre-signed URL mechanism to generate time-limited download links.</p>
     */
    @Override
    public String generateSignedUrl(String storagePath, Duration expiration) {
        Objects.requireNonNull(storagePath, "storagePath cannot be null");
        Objects.requireNonNull(expiration, "expiration cannot be null");

        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(storagePath)
                            .expiry((int) expiration.getSeconds(), TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            log.error("[MinIO] Failed to generate signed URL: path={}", storagePath, e);
            throw new RuntimeException("Failed to generate signed URL: " + e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Generates a pre-signed upload URL supporting client-side direct upload mode.</p>
     */
    @Override
    public String generatePresignedUploadUrl(String storagePath, Duration expiration) {
        Objects.requireNonNull(storagePath, "storagePath cannot be null");
        Objects.requireNonNull(expiration, "expiration cannot be null");

        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucketName)
                            .object(storagePath)
                            .expiry((int) expiration.getSeconds(), TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            log.error("[MinIO] Failed to generate upload signed URL: path={}", storagePath, e);
            throw new RuntimeException("Failed to generate upload signed URL: " + e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getStorageType() {
        return "minio";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void copy(String sourcePath, String targetPath) {
        Objects.requireNonNull(sourcePath, "sourcePath cannot be null");
        Objects.requireNonNull(targetPath, "targetPath cannot be null");

        try {
            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(bucketName)
                            .object(targetPath)
                            .source(CopySource.builder()
                                    .bucket(bucketName)
                                    .object(sourcePath)
                                    .build())
                            .build());
            log.info("[MinIO] File copied successfully: source={}, target={}", sourcePath, targetPath);
        } catch (Exception e) {
            log.error("[MinIO] File copy failed: source={}, target={}", sourcePath, targetPath, e);
            throw new RuntimeException("File copy failed: " + e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getFileSize(String storagePath) {
        Objects.requireNonNull(storagePath, "storagePath cannot be null");

        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(storagePath)
                            .build());
            return stat.size();
        } catch (Exception e) {
            log.error("[MinIO] Failed to get file size: path={}", storagePath, e);
            throw new RuntimeException("Failed to get file size: " + e.getMessage(), e);
        }
    }
}
