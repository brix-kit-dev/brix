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

/**
 * Support Package
 * 
 * <p>This package provides support classes (convenience wrappers) for module development:</p>
 * <ul>
 *   <li>{@link io.runtime.sdk.support.AbstractModule} - Module abstract base class</li>
 *   <li>{@link io.runtime.sdk.support.ModuleState} - Module state enumeration</li>
 *   <li>{@link io.runtime.sdk.support.ModuleInitializationException} - Module initialization exception</li>
 *   <li>{@link io.runtime.sdk.support.ModuleStartupException} - Module startup exception</li>
 * </ul>
 * 
 * <p>Note: These classes are "convenience wrappers", not capability implementations.
 * They only encapsulate calls to RuntimeContext, reducing boilerplate code for plugin developers.</p>
 * 
 * @since 3.0.0
 */
package io.runtime.sdk.support;
