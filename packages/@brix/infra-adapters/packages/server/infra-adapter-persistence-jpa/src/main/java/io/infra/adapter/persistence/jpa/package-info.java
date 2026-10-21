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
 * JPA implementation of the owner-scoped {@link io.runtime.sdk.capability.PersistenceCapability}
 * (Layer 2C adapter).
 *
 * <p>Implements root blueprint §7: every data owner gets its own persistence unit view, repositories
 * are created and validated here rather than by a framework container, units of work never span two
 * owners, and ORM or database failures are translated into stable error codes. All database-product
 * specific knowledge (SQL states, dialect behaviour) stays in this package; plugins only see the L2A
 * contract.</p>
 */
package io.infra.adapter.persistence.jpa;
