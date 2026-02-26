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
 * 支持类包
 * 
 * <p>本包提供模块开发的支持类（便利包装）：</p>
 * <ul>
 *   <li>{@link io.runtime.sdk.support.AbstractModule} - 模块抽象基类</li>
 *   <li>{@link io.runtime.sdk.support.ModuleState} - 模块状态枚举</li>
 *   <li>{@link io.runtime.sdk.support.ModuleInitializationException} - 模块初始化异常</li>
 *   <li>{@link io.runtime.sdk.support.ModuleStartupException} - 模块启动异常</li>
 * </ul>
 * 
 * <p>注意：这些类是"便利包装"，不是能力实现。它们仅封装对 RuntimeContext 的调用，
 * 减少插件开发者的样板代码。</p>
 * 
 * @since 3.0.0
 */
package io.runtime.sdk.support;
