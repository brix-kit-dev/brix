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
package io.brix.platform.fixturesecurity.jwt;

import io.jsonwebtoken.Jwts;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * A platform class listed in the test class path's jwt-library-users.txt and in no other list: only the JWT rule lets
 * it and its nested class through.
 */
public final class ListedTokenValidator {

    /**
     * Returns the JJWT type for bytecode import.
     *
     * @return the type
     */
    public Class<Jwts> jwt() {
        return Jwts.class;
    }

    /**
     * Returns the Spring Security crypto type for bytecode import: not on this class's list.
     *
     * @return the type
     */
    public Class<BCryptPasswordEncoder> crypto() {
        return BCryptPasswordEncoder.class;
    }

    /** A nested class of a listed class, let through with it. */
    public static final class Claims {

        /**
         * Returns the JJWT type for bytecode import.
         *
         * @return the type
         */
        public Class<Jwts> jwt() {
            return Jwts.class;
        }
    }
}
