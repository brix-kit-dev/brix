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
package io.runtime.sdk.plugin;

/**
 * Data scope granted together with a tenant permission.
 *
 * <p>{@link #TENANT} covers every object of the current tenant. {@link #SELF}
 * covers only the objects associated with the caller: the current member for a
 * tenant actor, the current principal for a tenant subject. The resource Owner
 * defines that association and applies the scope in its own repository
 * queries; the Runtime never guesses business relations.</p>
 *
 * @author Runtime SDK Team
 * @since 3.2.0
 */
public enum AccessScope {

    /** Only objects associated with the calling member or principal. */
    SELF,

    /** Every object of the current tenant. */
    TENANT;

    /**
     * Returns the wider of this scope and {@code other}.
     *
     * @param other another scope
     * @return {@link #TENANT} when either scope is {@link #TENANT}, otherwise {@link #SELF}
     */
    public AccessScope widest(AccessScope other) {
        return this == TENANT || other == TENANT ? TENANT : SELF;
    }
}
