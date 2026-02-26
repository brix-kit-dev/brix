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

import java.security.Principal;

/**
 * Tenant-Aware User Principal Interface
 * 
 * <p>Extends the standard {@link Principal} interface with tenant information support.
 * Used in multi-tenant systems to identify the tenant a user belongs to.</p>
 * 
 * <h3>Implementation Example</h3>
 * <pre>{@code
 * public class BrixUserPrincipal implements TenantAwarePrincipal {
 *     private final String userId;
 *     private final String username;
 *     private final String tenantId;
 *     
 *     @Override
 *     public String getName() {
 *         return username;
 *     }
 *     
 *     @Override
 *     public String getUserId() {
 *         return userId;
 *     }
 *     
 *     @Override
 *     public String getTenantId() {
 *         return tenantId;
 *     }
 * }
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see AuthContextCapability
 */
public interface TenantAwarePrincipal extends Principal {

    /**
     * Get unique user identifier
     * 
     * @return user ID
     */
    String getUserId();

    /**
     * Get tenant identifier
     * 
     * @return tenant ID, single-tenant scenarios may return a default value
     */
    String getTenantId();
}
