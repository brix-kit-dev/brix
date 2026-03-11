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
package io.brix.platform.common.util;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Platform Unified ID Generator, provides 9-digit numeric IDs and prefixed plugin/user identifiers,
 * ensuring consistent encoding rules.
 */
public final class IdGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int DEFAULT_LENGTH = 9;

    private IdGenerator() {
    }

    /**
     * Generate a 9-digit numeric ID, suitable for user, plugin primary keys.
     */
    public static String numericId() {
        return numericId(DEFAULT_LENGTH);
    }

    /**
     * Generate a numeric ID with specified length.
     *
     * @param length Target length
     * @return Numeric string of specified length
     */
    public static String numericId(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("ID length must be greater than 0");
        }
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(RANDOM.nextInt(10));
        }
        return builder.toString();
    }

    /**
     * Generate a user ID with USR prefix (e.g., USR-123456789).
     */
    public static String userId() {
        return format("USR", numericId());
    }

    /**
     * Generate a plugin ID with PLG prefix (e.g., PLG-123456789).
     */
    public static String pluginId() {
        return format("PLG", numericId());
    }

    private static String format(String prefix, String id) {
        return prefix.toUpperCase(Locale.ROOT) + '-' + id;
    }
}
