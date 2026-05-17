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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import io.runtime.sdk.capability.PasswordCapability;

/**
 * Auto-configuration that exposes the {@link PasswordCapability} contract
 * backed by {@link BCryptPasswordCapability}.
 *
 * <h3>Architectural Position</h3>
 * <p>Layer 2C wiring for a Layer 2A capability contract. Hosts and other
 * platform modules depend on the {@link PasswordCapability} interface only;
 * this auto-configuration provides the default BCrypt implementation when
 * Spring Security Crypto is on the classpath.</p>
 *
 * <h3>Configuration</h3>
 * <ul>
 *   <li>{@code brix.security.password.bcrypt.strength} (default {@code 10}) —
 *       BCrypt cost factor; valid range 4–31.</li>
 * </ul>
 *
 * <p>Hosts that supply their own {@link PasswordCapability} bean will
 * automatically suppress this default via {@link ConditionalOnMissingBean}.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@AutoConfiguration
@ConditionalOnClass(BCryptPasswordEncoder.class)
public class PasswordCapabilityAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PasswordCapabilityAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(PasswordCapability.class)
    public PasswordCapability passwordCapability(
            @Value("${brix.security.password.bcrypt.strength:10}") int strength) {
        log.info("Registering BCryptPasswordCapability: strength={}", strength);
        return new BCryptPasswordCapability(strength);
    }
}
