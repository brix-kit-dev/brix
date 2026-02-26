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
package io.runtime.sdk.capability;

import java.io.InputStream;
import java.time.Duration;

/**
 * 文件存储能力契约
 * 
 * <p>提供文件存储的抽象接口，使插件无需感知底层存储实现
 * （MinIO / S3 / Azure Blob / 本地文件系统）。
 * 插件通过此接口上传、下载、删除文件，由适配器层封装具体实现。</p>
 * 
 * <h3>核心职责</h3>
 * <ul>
 *   <li>文件上传：支持流式上传、指定 Content-Type</li>
 *   <li>文件下载：返回输入流供插件读取</li>
 *   <li>文件管理：删除、复制、存在性检查、文件大小获取</li>
 *   <li>签名 URL：支持生成临时访问 URL 和上传 URL</li>
 * </ul>
 * 
 * <h3>设计约束</h3>
 * <ul>
 *   <li><b>插件透明</b>：插件代码中不出现 MinIO / S3 SDK 等基础设施依赖</li>
 *   <li><b>统一路径</b>：存储路径格式由插件定义，适配器负责映射到具体存储</li>
 *   <li><b>安全访问</b>：签名 URL 支持自定义有效期</li>
 * </ul>
 * 
 * <h3>蓝图对照</h3>
 * <p>对应蓝图 v3.0.2 红线 1「插件不得直接依赖基础设施」的修复方案。
 * 能力级别为 STANDARD（标准能力）。</p>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Inject
 * private FileStorageCapability fileStorage;
 * 
 * public String uploadAvatar(InputStream input, String contentType, long size) {
 *     String path = "avatars/" + userId + "/avatar.jpg";
 *     return fileStorage.upload(path, input, contentType, size);
 * }
 * 
 * public String getAvatarUrl(String path) {
 *     return fileStorage.generateSignedUrl(path, Duration.ofMinutes(30));
 * }
 * }</pre>
 * 
 * <h3>实现说明</h3>
 * <p>此接口由基础设施适配器层（Layer 2.5）实现：</p>
 * <ul>
 *   <li>{@code infra-adapter-minio}：MinIO / S3 兼容存储实现</li>
 *   <li>Fallback：本地文件系统实现（开发环境）</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public interface FileStorageCapability {

    /**
     * 上传文件
     * 
     * @param storagePath 存储路径（相对路径），如 {@code "avatars/user123/photo.jpg"}
     * @param inputStream 文件输入流
     * @param contentType MIME 类型，如 {@code "image/jpeg"}
     * @param fileSize    文件大小（字节）
     * @return 上传后的完整存储路径
     * @throws IllegalArgumentException 如果参数无效
     */
    String upload(String storagePath, InputStream inputStream, String contentType, long fileSize);

    /**
     * 下载文件
     * 
     * <p>返回文件输入流，调用方负责关闭流。</p>
     * 
     * @param storagePath 存储路径
     * @return 文件输入流
     * @throws IllegalArgumentException 如果路径为 null
     */
    InputStream download(String storagePath);

    /**
     * 删除文件
     * 
     * <p>如果文件不存在，此方法不会抛出异常。</p>
     * 
     * @param storagePath 存储路径
     */
    void delete(String storagePath);

    /**
     * 检查文件是否存在
     * 
     * @param storagePath 存储路径
     * @return 如果文件存在返回 true
     */
    boolean exists(String storagePath);

    /**
     * 生成签名下载 URL
     * 
     * <p>生成带签名的临时访问 URL，用于安全的文件下载。
     * 签名 URL 有时效性，过期后无法访问。</p>
     * 
     * @param storagePath 存储路径
     * @param expiration  URL 有效期
     * @return 签名下载 URL
     */
    String generateSignedUrl(String storagePath, Duration expiration);

    /**
     * 生成签名下载 URL（默认 5 分钟有效期）
     * 
     * @param storagePath 存储路径
     * @return 签名下载 URL
     */
    default String generateSignedUrl(String storagePath) {
        return generateSignedUrl(storagePath, Duration.ofMinutes(5));
    }

    /**
     * 生成预签名上传 URL
     * 
     * <p>用于客户端直传模式，客户端可直接上传文件到存储后端，
     * 减轻应用服务器带宽压力。</p>
     * 
     * @param storagePath 存储路径
     * @param expiration  URL 有效期
     * @return 预签名上传 URL
     */
    String generatePresignedUploadUrl(String storagePath, Duration expiration);

    /**
     * 生成预签名上传 URL（默认 15 分钟有效期）
     * 
     * @param storagePath 存储路径
     * @return 预签名上传 URL
     */
    default String generatePresignedUploadUrl(String storagePath) {
        return generatePresignedUploadUrl(storagePath, Duration.ofMinutes(15));
    }

    /**
     * 获取存储类型名称
     * 
     * @return 存储类型（如 {@code "minio"}、{@code "s3"}、{@code "local"}）
     */
    String getStorageType();

    /**
     * 复制文件
     * 
     * @param sourcePath 源文件路径
     * @param targetPath 目标文件路径
     */
    void copy(String sourcePath, String targetPath);

    /**
     * 获取文件大小
     * 
     * @param storagePath 存储路径
     * @return 文件大小（字节）
     */
    long getFileSize(String storagePath);
}
