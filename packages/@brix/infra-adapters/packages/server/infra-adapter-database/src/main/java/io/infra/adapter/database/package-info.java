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
 * Database capability adapter implementation package.
 * 
 * <p>This package provides HikariCP-based {@link io.runtime.sdk.capability.DatabaseCapability} implementation,
 * part of the infrastructure adapter layer (Layer 2.5: Adapter Layer).</p>
 * 
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link io.infra.adapter.database.HikariDatabaseCapability} - HikariCP-based database capability implementation</li>
 * </ul>
 * 
 * <h2>Design Principles</h2>
 * <ul>
 *   <li>Follows Runtime Shell architecture constraints, does not expose database driver details to plugins</li>
 *   <li>Supports configuration-driven multi-database vendor switching</li>
 *   <li>Assembled by Host layer via dependency injection</li>
 * </ul>
 * 
 * <h2>Architecture Compliance</h2>
 * <p>Database infrastructure adapter package.</p>
 * 
 * @author Brix Platform Authors
 * @since 3.0.0
 */
package io.infra.adapter.database;
