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

import io.runtime.sdk.annotation.Since;

/**
 * Password Capability Contract
 *
 * <p>Provides framework-agnostic password hashing and verification.
 * Plugins obtain this capability through the Runtime Context instead of
 * implementing their own hashing logic, ensuring a single, auditable
 * password-handling strategy across the platform.</p>
 *
 * <h3>Core Responsibilities</h3>
 * <ul>
 *   <li><b>Hash</b>: One-way hash with auto-generated salt (e.g., BCrypt)</li>
 *   <li><b>Verify</b>: Constant-time comparison to prevent timing attacks</li>
 * </ul>
 *
 * <h3>Security Requirements</h3>
 * <ul>
 *   <li>Implementations MUST use a secure one-way hash algorithm (BCrypt, Argon2, etc.)</li>
 *   <li>Implementations MUST auto-generate a random salt per hash call</li>
 *   <li>MD5, SHA-1, and other weak hashes are PROHIBITED</li>
 *   <li>Verification MUST use constant-time comparison</li>
 * </ul>
 *
 * <h3>Architectural Position</h3>
 * <p>Defined in Layer 2A (Capability Contract). Default implementation (BCrypt)
 * is provided by {@code platform-auth} (Layer 2C). Plugins at Layer 1
 * depend only on this contract.</p>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @Inject
 * private PasswordCapability password;
 *
 * public void registerUser(String rawPassword) {
 *     String hashed = password.hash(rawPassword);
 *     userRepository.save(new User(hashed));
 * }
 *
 * public boolean authenticate(String rawPassword, String storedHash) {
 *     return password.verify(rawPassword, storedHash);
 * }
 * }</pre>
 *
 * @author Runtime SDK Team
 * @since 3.2.0
 * @see ObservabilityCapability
 */
@Since("3.2.0")
public interface PasswordCapability {

    /**
     * Hash a raw password using a secure one-way algorithm.
     *
     * <p>Each invocation generates a unique salt, so identical passwords
     * produce different hashes. The returned value includes algorithm
     * identifier, salt, and ciphertext (e.g., BCrypt {@code $2a$10$...}).</p>
     *
     * @param rawPassword the plaintext password; must not be {@code null} or empty
     * @return the encoded hash suitable for persistent storage
     * @throws IllegalArgumentException if {@code rawPassword} is {@code null} or empty
     */
    String hash(String rawPassword);

    /**
     * Verify a raw password against a previously stored hash.
     *
     * <p>Extracts salt and algorithm parameters from {@code encodedPassword},
     * re-hashes {@code rawPassword}, and compares using constant-time equality.</p>
     *
     * @param rawPassword     the plaintext password to verify
     * @param encodedPassword the stored hash to compare against
     * @return {@code true} if the password matches, {@code false} otherwise
     * @throws IllegalArgumentException if either argument is {@code null}
     */
    boolean verify(String rawPassword, String encodedPassword);
}
