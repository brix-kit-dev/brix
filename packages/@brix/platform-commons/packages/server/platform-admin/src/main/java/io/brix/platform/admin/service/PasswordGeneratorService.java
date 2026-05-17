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
package io.brix.platform.admin.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Service;

/**
 * Cryptographically secure temporary password generator.
 *
 * <h3>Password Policy</h3>
 * <ul>
 *   <li>Length: 16 characters</li>
 *   <li>Must contain at least one uppercase letter</li>
 *   <li>Must contain at least one lowercase letter</li>
 *   <li>Must contain at least one digit</li>
 *   <li>Must contain at least one special character from the defined set</li>
 * </ul>
 *
 * <h3>Security</h3>
 * <ul>
 *   <li>Uses {@link SecureRandom} — cryptographically strong, not {@code Math.random()}</li>
 *   <li>Generated passwords MUST NEVER be logged, stored (except as hash), or
 *       included in audit event {@code description} / {@code reason} fields
 *       (SSOT §10 Red-Line R-10)</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@Service
public class PasswordGeneratorService {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // no I, O (ambiguous)
    private static final String LOWER = "abcdefghjkmnpqrstuvwxyz";  // no i, l, o (ambiguous)
    private static final String DIGITS = "23456789";                  // no 0, 1 (ambiguous)
    private static final String SPECIAL = "!@#$%^&*()-_=+";
    private static final String ALL = UPPER + LOWER + DIGITS + SPECIAL;

    private static final int PASSWORD_LENGTH = 16;

    private final SecureRandom rng = new SecureRandom();

    /**
     * Generates a 16-character cryptographically secure temporary password that satisfies
     * the platform password policy.
     *
     * <p><b>SECURITY:</b> The returned value MUST be treated as a secret from this point
     * forward. Pass it to the response DTO only; never log, print, or audit-record it.
     *
     * @return a random password string conforming to the platform password policy
     */
    public String generate() {
        // Guarantee at least one character from each required category.
        char[] mandatory = {
            UPPER.charAt(rng.nextInt(UPPER.length())),
            LOWER.charAt(rng.nextInt(LOWER.length())),
            DIGITS.charAt(rng.nextInt(DIGITS.length())),
            SPECIAL.charAt(rng.nextInt(SPECIAL.length()))
        };

        char[] password = new char[PASSWORD_LENGTH];

        // Place mandatory characters in the first four positions.
        for (int i = 0; i < mandatory.length; i++) {
            password[i] = mandatory[i];
        }

        // Fill the remaining positions with characters from the full alphabet.
        for (int i = mandatory.length; i < PASSWORD_LENGTH; i++) {
            password[i] = ALL.charAt(rng.nextInt(ALL.length()));
        }

        // Shuffle using Fisher-Yates to avoid predictable positions.
        for (int i = PASSWORD_LENGTH - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            char temp = password[i];
            password[i] = password[j];
            password[j] = temp;
        }

        return new String(password);
    }
}
