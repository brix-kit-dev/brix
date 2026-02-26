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

/**
 * MinIO 文件存储能力实现包
 * 
 * <p>本包提供基于 MinIO（S3 兼容）的 {@link io.runtime.sdk.capability.FileStorageCapability} 实现，
 * 是基础设施适配器层（Layer 2.5: Adapter 层）的组件之一。</p>
 * 
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link io.infra.adapter.minio.MinioFileStorageCapability} - 基于 MinIO 的文件存储能力实现</li>
 * </ul>
 * 
 * <h2>设计原则</h2>
 * <ul>
 *   <li>遵循运行壳架构约束，不暴露 MinIO SDK 细节给插件</li>
 *   <li>插件通过 FileStorageCapability 契约访问文件存储</li>
 *   <li>支持 S3 兼容的对象存储服务</li>
 * </ul>
 * 
 * @author Brix Platform Authors
 * @since 3.0.0
 */
package io.infra.adapter.minio;
