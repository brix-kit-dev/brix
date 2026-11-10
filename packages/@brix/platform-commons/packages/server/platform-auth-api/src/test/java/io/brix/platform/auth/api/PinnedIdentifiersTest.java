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
package io.brix.platform.auth.api;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

/**
 * Published role and permission IDs never change and are never reused (super-admin 4.0.1 §3.3; standard-link plan
 * queue item 4, D118).
 *
 * <p>The IDs live in tokens, seeds and endpoint descriptors, and javac copies each constant into the code that uses
 * it, so a changed value breaks consumers without a compile error. Adding an ID means adding it here in the same
 * change, where review sees it; changing or removing one fails.</p>
 */
class PinnedIdentifiersTest {

    @Test
    void roleIdsArePinned() throws IllegalAccessException {
        assertEquals(Map.of("PLATFORM_SUPER_ADMIN", "PLATFORM_SUPER_ADMIN"), identifiers(RoleCode.class));
    }

    @Test
    void permissionIdsArePinned() throws IllegalAccessException {
        assertEquals(Map.ofEntries(
                entry("PLATFORM_ADMIN_READ", "platform:admin:read"),
                entry("PLATFORM_ADMIN_CREATE", "platform:admin:create"),
                entry("PLATFORM_ADMIN_REVOKE", "platform:admin:revoke"),
                entry("PLATFORM_ADMIN_RESET_PASSWORD", "platform:admin:reset-password"),
                entry("PLATFORM_ADMIN_CHANGE_OWN_PASSWORD", "platform:admin:change-own-password"),
                entry("PLATFORM_AUDIT_READ", "platform:audit:read"),
                entry("PLATFORM_TENANT_READ", "platform:tenant:read"),
                entry("PLATFORM_TENANT_CREATE", "platform:tenant:create"),
                entry("PLATFORM_TENANT_UPDATE_STATUS", "platform:tenant:update-status"),
                entry("PLATFORM_TENANT_FIRST_OWNER_INVITE", "platform:tenant:first-owner-invite")),
            identifiers(PermissionCode.class));
    }

    /** The IDs a catalog declares, by constant name. A catalog declares nothing else and has no instances. */
    private static Map<String, String> identifiers(Class<?> catalog) throws IllegalAccessException {
        assertTrue(Modifier.isFinal(catalog.getModifiers()), () -> catalog.getSimpleName() + " is final");
        for (Constructor<?> constructor : catalog.getDeclaredConstructors()) {
            assertTrue(Modifier.isPrivate(constructor.getModifiers()),
                () -> catalog.getSimpleName() + " has only a private constructor");
        }
        Map<String, String> identifiers = new TreeMap<>();
        for (Field field : catalog.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue; // a coverage agent adds its own field
            }
            int modifiers = field.getModifiers();
            assertTrue(Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)
                    && field.getType() == String.class,
                () -> catalog.getSimpleName() + "." + field.getName() + " is a public static final String");
            identifiers.put(field.getName(), (String) field.get(null));
        }
        return identifiers;
    }
}
