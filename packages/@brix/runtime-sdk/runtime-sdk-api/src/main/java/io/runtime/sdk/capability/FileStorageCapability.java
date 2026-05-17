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

import io.runtime.sdk.annotation.Since;

/**
 * File Storage Capability Contract
 * 
 * <p>Provides an abstract interface for file storage, allowing plugins to operate without
 * knowledge of the underlying storage implementation (MinIO / S3 / Azure Blob / Local filesystem).
 * Plugins upload, download, and delete files through this interface, with the adapter layer encapsulating specific implementations.</p>
 * 
 * <h3>Core Responsibilities</h3>
 * <ul>
 *   <li>File upload: Supports streaming upload with Content-Type specification</li>
 *   <li>File download: Returns input stream for plugin reading</li>
 *   <li>File management: Delete, copy, existence check, size retrieval</li>
 *   <li>Signed URLs: Supports temporary access URL and upload URL generation</li>
 * </ul>
 * 
 * <h3>Design Constraints</h3>
 * <ul>
 *   <li><b>Plugin Transparency</b>: No MinIO / S3 SDK infrastructure dependencies in plugin code</li>
 *   <li><b>Unified Paths</b>: Storage path format defined by plugins, adapter maps to specific storage</li>
 *   <li><b>Secure Access</b>: Signed URLs support custom expiration periods</li>
 * </ul>
 * 
 * <h3>Architecture Compliance</h3>
 * <p>Abstracts file storage to prevent plugins from directly depending on infrastructure.
 * Capability level: STANDARD.</p>
 * 
 * <h3>Usage Example</h3>
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
 * <h3>Implementation Notes</h3>
 * <p>This interface is implemented by infrastructure adapter layer (Layer 2.5):</p>
 * <ul>
 *   <li>{@code infra-adapter-minio}: MinIO / S3 compatible storage implementation</li>
 *   <li>Fallback: Local filesystem implementation (development environment)</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
@Since("3.0.0")
public interface FileStorageCapability {

    /**
     * Uploads a file
     * 
     * @param storagePath the storage path (relative path), e.g., {@code "avatars/user123/photo.jpg"}
     * @param inputStream the file input stream
     * @param contentType the MIME type, e.g., {@code "image/jpeg"}
     * @param fileSize    the file size in bytes
     * @return the complete storage path after upload
     * @throws IllegalArgumentException if parameters are invalid
     */
    String upload(String storagePath, InputStream inputStream, String contentType, long fileSize);

    /**
     * Downloads a file
     * 
     * <p>Returns the file input stream. The caller is responsible for closing the stream.</p>
     * 
     * @param storagePath the storage path
     * @return the file input stream
     * @throws IllegalArgumentException if path is null
     */
    InputStream download(String storagePath);

    /**
     * Deletes a file
     * 
     * <p>If the file does not exist, this method will not throw an exception.</p>
     * 
     * @param storagePath the storage path
     */
    void delete(String storagePath);

    /**
     * Checks if a file exists
     * 
     * @param storagePath the storage path
     * @return true if the file exists
     */
    boolean exists(String storagePath);

    /**
     * Generates a signed download URL
     * 
     * <p>Generates a temporary signed URL for secure file downloads.
     * Signed URLs are time-limited and become invalid after expiration.</p>
     * 
     * @param storagePath the storage path
     * @param expiration  the URL validity period
     * @return the signed download URL
     */
    String generateSignedUrl(String storagePath, Duration expiration);

    /**
     * Generates a signed download URL (5-minute validity by default)
     * 
     * @param storagePath the storage path
     * @return the signed download URL
     */
    default String generateSignedUrl(String storagePath) {
        return generateSignedUrl(storagePath, Duration.ofMinutes(5));
    }

    /**
     * Generates a presigned upload URL
     * 
     * <p>Used for client direct upload mode, where clients can upload files directly
     * to the storage backend, reducing bandwidth pressure on application servers.</p>
     * 
     * @param storagePath the storage path
     * @param expiration  the URL validity period
     * @return the presigned upload URL
     */
    String generatePresignedUploadUrl(String storagePath, Duration expiration);

    /**
     * Generates a presigned upload URL (15-minute validity by default)
     * 
     * @param storagePath the storage path
     * @return the presigned upload URL
     */
    default String generatePresignedUploadUrl(String storagePath) {
        return generatePresignedUploadUrl(storagePath, Duration.ofMinutes(15));
    }

    /**
     * Gets the storage type name
     * 
     * @return the storage type (e.g., {@code "minio"}, {@code "s3"}, {@code "local"})
     */
    String getStorageType();

    /**
     * Copies a file
     * 
     * @param sourcePath the source file path
     * @param targetPath the target file path
     */
    void copy(String sourcePath, String targetPath);

    /**
     * Gets the file size
     * 
     * @param storagePath the storage path
     * @return the file size in bytes
     */
    long getFileSize(String storagePath);
}
