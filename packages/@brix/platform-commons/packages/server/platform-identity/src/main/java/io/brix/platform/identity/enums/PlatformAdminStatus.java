/*
 * Copyright 2026 Brix Platform Authors
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
package io.brix.platform.identity.enums;

/**
 * Authorization lifecycle for platform administrator grants.
 *
 * <p>This status applies only to {@code sys_platform_admin}. Revoking a grant
 * prevents PLATFORM token issuance but does not disable the underlying
 * identity, so tenant-scoped identities can continue to use tenant login.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public enum PlatformAdminStatus {

    /** The identity currently has platform administrator authorization. */
    ACTIVE,

    /** Platform administrator authorization has been revoked. */
    REVOKED
}