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
 * Multi-module Flyway migration support for the Brix database adapter.
 *
 * <p>This package provides configuration-driven, per-module Flyway migration orchestration.
 * Previously located in the Host layer as {@code FlywayModuleConfig}, this logic has been
 * migrated to the infrastructure adapter layer (Layer 2C) per the Ultra-Thin Host principle.</p>
 *
 * <p>Key classes:</p>
 * <ul>
 *   <li>{@link io.infra.adapter.database.migration.FlywayModuleMigrationAutoConfiguration} — Spring Boot auto-configuration</li>
 *   <li>{@link io.infra.adapter.database.migration.FlywayModuleMigrationProperties} — Externalized configuration</li>
 *   <li>{@link io.infra.adapter.database.migration.FlywayModuleMigrationRunner} — Migration execution engine</li>
 * </ul>
 *
 * @since 3.1.0
 */
package io.infra.adapter.database.migration;
