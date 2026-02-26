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
 * 基于 HTTP Webhook 的事件总线适配器（开源）
 * 
 * <p>本包提供 EventBusCapability 的 HTTP Webhook 实现，适用于嵌入模式部署。
 * 通过 HTTP POST 请求将事件推送到配置的外部端点。</p>
 * 
 * <h2>核心类</h2>
 * <ul>
 *   <li>{@link io.infra.adapter.webhook.HttpWebhookEventBus} - HTTP Webhook 事件总线</li>
 *   <li>{@link io.infra.adapter.webhook.WebhookSignatureVerifier} - Webhook 签名验证器</li>
 *   <li>{@link io.infra.adapter.webhook.WebhookRetryHandler} - 重试处理器</li>
 * </ul>
 * 
 * <h2>适用场景</h2>
 * <ul>
 *   <li>嵌入模式部署（无需 Kafka）</li>
 *   <li>与外部系统的 Webhook 集成</li>
 *   <li>轻量级事件通知</li>
 *   <li>单体应用部署</li>
 * </ul>
 * 
 * <h2>安全特性</h2>
 * <ul>
 *   <li>HMAC-SHA256 签名验证</li>
 *   <li>时间戳防重放攻击</li>
 *   <li>可配置的重试策略</li>
 * </ul>
 * 
 * <h2>架构分层</h2>
 * <p>本包属于 Layer 2 - Adapter 层，实现 Layer 1 定义的能力接口。</p>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
package io.infra.adapter.webhook;
