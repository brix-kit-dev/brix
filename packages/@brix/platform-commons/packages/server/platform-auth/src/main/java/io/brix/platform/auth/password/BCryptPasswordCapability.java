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
package io.brix.platform.auth.password;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import io.runtime.sdk.annotation.Since;
import io.runtime.sdk.capability.PasswordCapability;

/**
 * BCrypt-based PasswordCapability implementation.
 *
 * <p>Delegates to Spring Security's {@link BCryptPasswordEncoder}, which provides
 * a battle-tested BCrypt implementation with constant-time comparison.</p>
 *
 * <h3>Architectural Position</h3>
 * <p>Layer 2C (Platform Implementation). Plugins at Layer 1 depend only on the
 * {@link PasswordCapability} contract from Layer 2A ({@code runtime-sdk-api}).</p>
 *
 * <h3>Thread Safety</h3>
 * <p>This class is thread-safe and can be used as a singleton bean.
 * The underlying {@link BCryptPasswordEncoder} is also thread-safe.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @see PasswordCapability
 */
@Since("3.2.0")
public class BCryptPasswordCapability implements PasswordCapability {

    private final BCryptPasswordEncoder encoder;

    /**
     * Creates an instance with the default BCrypt strength (10).
     */
    public BCryptPasswordCapability() {
        this(10);
    }

    /**
     * Creates an instance with the specified BCrypt strength.
     *
     * @param strength the BCrypt cost factor (recommended range: 10–14)
     * @throws IllegalArgumentException if strength is not between 4 and 31
     */
    public BCryptPasswordCapability(int strength) {
        if (strength < 4 || strength > 31) {
            throw new IllegalArgumentException(
                "BCrypt strength must be between 4 and 31, got: " + strength);
        }
        this.encoder = new BCryptPasswordEncoder(strength);
    }

    @Override
    public String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("rawPassword must not be null or empty");
        }
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean verify(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            throw new IllegalArgumentException("rawPassword and encodedPassword must not be null");
        }
        return encoder.matches(rawPassword, encodedPassword);
    }
}
