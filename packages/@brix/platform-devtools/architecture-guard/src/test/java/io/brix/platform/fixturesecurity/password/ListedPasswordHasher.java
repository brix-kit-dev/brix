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
package io.brix.platform.fixturesecurity.password;

import io.jsonwebtoken.Jwts;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * A platform class listed in the test class path's spring-crypto-users.txt and in no other list: only the crypto rule
 * lets it through.
 */
public final class ListedPasswordHasher {

    /**
     * Returns the Spring Security crypto type for bytecode import.
     *
     * @return the type
     */
    public Class<BCryptPasswordEncoder> crypto() {
        return BCryptPasswordEncoder.class;
    }

    /**
     * Returns the JJWT type for bytecode import: not on this class's list.
     *
     * @return the type
     */
    public Class<Jwts> jwt() {
        return Jwts.class;
    }
}
