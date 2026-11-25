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
package io.brix.platform.fixturesecurity.service;

import com.auth0.jwt.JWT;
import io.jsonwebtoken.Jwts;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;

/**
 * A platform plugin of one artifact, without a package named core, that parses tokens and hashes passwords itself:
 * forbidden (root S-1).
 */
public final class AuthenticatesItself {

    /**
     * Returns the JJWT type for bytecode import.
     *
     * @return the type
     */
    public Class<Jwts> jwt() {
        return Jwts.class;
    }

    /**
     * Returns the Auth0 type for bytecode import.
     *
     * @return the type
     */
    public Class<JWT> auth0() {
        return JWT.class;
    }

    /**
     * Returns the Spring Security crypto type for bytecode import.
     *
     * @return the type
     */
    public Class<BCryptPasswordEncoder> crypto() {
        return BCryptPasswordEncoder.class;
    }

    /**
     * Returns the Spring OAuth2 client type for bytecode import.
     *
     * @return the type
     */
    public Class<OAuth2AuthorizedClient> oauth2() {
        return OAuth2AuthorizedClient.class;
    }
}
