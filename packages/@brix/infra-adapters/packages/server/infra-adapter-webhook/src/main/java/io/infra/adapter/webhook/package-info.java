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
 * HTTP Webhook-based Event Bus Adapter (Open Source)
 * 
 * <p>This package provides an HTTP Webhook implementation of EventBusCapability,
 * suitable for embedded deployment. Pushes events to configured external endpoints
 * via HTTP POST requests.</p>
 * 
 * <h2>Core Classes</h2>
 * <ul>
 *   <li>{@link io.infra.adapter.webhook.HttpWebhookEventBus} - HTTP Webhook event bus</li>
 *   <li>{@link io.infra.adapter.webhook.WebhookSignatureVerifier} - Webhook signature verifier</li>
 *   <li>{@link io.infra.adapter.webhook.WebhookRetryHandler} - Retry handler</li>
 * </ul>
 * 
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Embedded deployment (without Kafka)</li>
 *   <li>Webhook integration with external systems</li>
 *   <li>Lightweight event notifications</li>
 *   <li>Monolithic application deployment</li>
 * </ul>
 * 
 * <h2>Security Features</h2>
 * <ul>
 *   <li>HMAC-SHA256 signature verification</li>
 *   <li>Timestamp-based replay attack prevention</li>
 *   <li>Configurable retry policies</li>
 * </ul>
 * 
 * <h2>Architecture Layer</h2>
 * <p>This package belongs to Layer 2 - Adapter layer, implementing capability interfaces defined in Layer 1.</p>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
package io.infra.adapter.webhook;
