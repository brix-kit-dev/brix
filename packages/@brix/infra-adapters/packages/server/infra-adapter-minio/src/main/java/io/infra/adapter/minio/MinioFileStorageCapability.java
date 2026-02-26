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

import io.runtime.sdk.capability.FileStorageCapability;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;

import io.minio.*;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 基于 MinIO 的文件存储能力实现
 * 
 * <p>本类是 {@link FileStorageCapability} 的生产实现，
 * 提供基于 MinIO（S3 兼容 API）的分布式对象存储能力。
 * 插件通过此实现上传、下载、管理文件，无需感知 MinIO SDK 的存在。</p>
 * 
 * <h3>核心特性</h3>
 * <ul>
 *   <li><b>S3 兼容</b>：基于 MinIO SDK，兼容所有 S3 API 存储</li>
 *   <li><b>Bucket 自动管理</b>：首次使用时自动创建 Bucket</li>
 *   <li><b>签名 URL</b>：支持预签名的下载和上传 URL</li>
 *   <li><b>文件元信息</b>：支持查询文件大小、存在性检查</li>
 * </ul>
 * 
 * <h3>蓝图对照</h3>
 * <p>对应蓝图 v3.0.2 红线 1 修复方案：将 MinIO 访问从插件层（shinwa-solutions）
 * 移至适配器层（Layer 2.5），插件通过能力契约访问。
 * 参考现有模式：{@code infra-adapter-kafka}、{@code infra-adapter-redis}。</p>
 * 
 * <h3>线程安全</h3>
 * <p>本类是线程安全的。MinioClient 支持并发访问。</p>
 * 
 * @author Brix Platform Authors
 * @since 3.0.0
 * @see FileStorageCapability
 */
@Capability(
    type = FileStorageCapability.class,
    name = "minio-file-storage",
    description = "基于 MinIO (S3兼容) 的文件存储能力实现",
    level = CapabilityLevel.STANDARD,
    aliases = {"fileStorage", "minioFileStorage"}
)
public class MinioFileStorageCapability implements FileStorageCapability {

    private static final Logger log = LoggerFactory.getLogger(MinioFileStorageCapability.class);

    /**
     * MinIO 客户端
     * 
     * <p>由自动配置类根据外部配置创建，封装了 S3 兼容 API 调用。</p>
     */
    private final MinioClient minioClient;

    /**
     * 默认 Bucket 名称
     */
    private final String bucketName;

    /**
     * 构造函数
     * 
     * @param minioClient MinIO 客户端实例
     * @param bucketName  默认 Bucket 名称
     */
    public MinioFileStorageCapability(MinioClient minioClient, String bucketName) {
        this.minioClient = Objects.requireNonNull(minioClient, "minioClient 不能为空");
        this.bucketName = Objects.requireNonNull(bucketName, "bucketName 不能为空");

        // 确保 Bucket 存在
        ensureBucketExists();

        log.info("[MinIO] 文件存储能力初始化完成: bucket={}", bucketName);
    }

    /**
     * 确保 Bucket 存在
     * 
     * <p>如果目标 Bucket 不存在，自动创建。此操作仅在适配器初始化时执行一次。</p>
     */
    private void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("[MinIO] 创建存储桶: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("[MinIO] 检查/创建存储桶失败: bucket={}", bucketName, e);
            throw new RuntimeException("MinIO 初始化失败: " + e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     * 
     * <p>将文件上传到 MinIO 指定路径，自动设置 Content-Type。</p>
     */
    @Override
    public String upload(String storagePath, InputStream inputStream, String contentType, long fileSize) {
        Objects.requireNonNull(storagePath, "storagePath 不能为空");
        Objects.requireNonNull(inputStream, "inputStream 不能为空");

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(storagePath)
                            .stream(inputStream, fileSize, -1)
                            .contentType(contentType)
                            .build());

            log.info("[MinIO] 文件上传成功: bucket={}, path={}, size={}", bucketName, storagePath, fileSize);
            return storagePath;
        } catch (Exception e) {
            log.error("[MinIO] 文件上传失败: path={}", storagePath, e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStream download(String storagePath) {
        Objects.requireNonNull(storagePath, "storagePath 不能为空");

        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(storagePath)
                            .build());
        } catch (Exception e) {
            log.error("[MinIO] 文件下载失败: path={}", storagePath, e);
            throw new RuntimeException("文件下载失败: " + e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(String storagePath) {
        Objects.requireNonNull(storagePath, "storagePath 不能为空");

        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(storagePath)
                            .build());
            log.info("[MinIO] 文件删除成功: path={}", storagePath);
        } catch (Exception e) {
            log.error("[MinIO] 文件删除失败: path={}", storagePath, e);
            throw new RuntimeException("文件删除失败: " + e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean exists(String storagePath) {
        Objects.requireNonNull(storagePath, "storagePath 不能为空");

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
     * <p>使用 MinIO 预签名 URL 机制生成带时效的下载链接。</p>
     */
    @Override
    public String generateSignedUrl(String storagePath, Duration expiration) {
        Objects.requireNonNull(storagePath, "storagePath 不能为空");
        Objects.requireNonNull(expiration, "expiration 不能为空");

        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(storagePath)
                            .expiry((int) expiration.getSeconds(), TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            log.error("[MinIO] 生成签名 URL 失败: path={}", storagePath, e);
            throw new RuntimeException("生成签名 URL 失败: " + e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     * 
     * <p>生成预签名上传 URL，支持客户端直传模式。</p>
     */
    @Override
    public String generatePresignedUploadUrl(String storagePath, Duration expiration) {
        Objects.requireNonNull(storagePath, "storagePath 不能为空");
        Objects.requireNonNull(expiration, "expiration 不能为空");

        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucketName)
                            .object(storagePath)
                            .expiry((int) expiration.getSeconds(), TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            log.error("[MinIO] 生成上传签名 URL 失败: path={}", storagePath, e);
            throw new RuntimeException("生成上传签名 URL 失败: " + e.getMessage(), e);
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
        Objects.requireNonNull(sourcePath, "sourcePath 不能为空");
        Objects.requireNonNull(targetPath, "targetPath 不能为空");

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
            log.info("[MinIO] 文件复制成功: source={}, target={}", sourcePath, targetPath);
        } catch (Exception e) {
            log.error("[MinIO] 文件复制失败: source={}, target={}", sourcePath, targetPath, e);
            throw new RuntimeException("文件复制失败: " + e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getFileSize(String storagePath) {
        Objects.requireNonNull(storagePath, "storagePath 不能为空");

        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(storagePath)
                            .build());
            return stat.size();
        } catch (Exception e) {
            log.error("[MinIO] 获取文件大小失败: path={}", storagePath, e);
            throw new RuntimeException("获取文件大小失败: " + e.getMessage(), e);
        }
    }
}
