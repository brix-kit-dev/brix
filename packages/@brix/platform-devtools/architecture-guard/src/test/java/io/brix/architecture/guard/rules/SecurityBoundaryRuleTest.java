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
package io.brix.architecture.guard.rules;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.auth0.jwt.JWT;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import io.brix.platform.fixture.CompliantPlatformService;
import io.brix.platform.fixturesecurity.jwt.ListedTokenValidator;
import io.brix.platform.fixturesecurity.password.ListedPasswordHasher;
import io.brix.samples.fixturealpha.core.AlphaPrice;
import io.jsonwebtoken.Jwts;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;

/**
 * Proves that the security rules check every class of a module, whatever its package: a platform plugin of one
 * artifact without a core package, and a business plugin's server. A listed class passes its own rule only, and a
 * module without classes fails.
 */
class SecurityBoundaryRuleTest {

    private static final List<Class<?>> VIOLATING = List.of(
        io.brix.platform.fixturesecurity.service.AuthenticatesItself.class,
        io.brix.samples.fixturesecurity.server.AuthenticatesItself.class);

    @Test
    @DisplayName("A platform plugin's service class that uses a JWT library fails, and a business plugin's server class")
    void jwtLibraryFails() {
        assertRejectsOnly(SecurityBoundaryRule.noDirectJwtUsage(), Jwts.class);
    }

    @Test
    @DisplayName("A platform plugin's service class that uses Auth0 fails, and a business plugin's server class")
    void auth0Fails() {
        assertRejectsOnly(SecurityBoundaryRule.noDirectAuth0Usage(), JWT.class);
    }

    @Test
    @DisplayName("A platform plugin's service class that uses Spring Security crypto fails, and a plugin's server class")
    void springCryptoFails() {
        assertRejectsOnly(SecurityBoundaryRule.noDirectCryptoUsage(), BCryptPasswordEncoder.class);
    }

    @Test
    @DisplayName("A platform plugin's service class that uses the OAuth2 client fails, and a plugin's server class")
    void oauth2ClientFails() {
        assertRejectsOnly(SecurityBoundaryRule.noDirectOAuth2ClientUsage(), OAuth2AuthorizedClient.class);
    }

    @Test
    @DisplayName("The combined rule refuses each of the four libraries")
    void combinedRuleRefusesEveryLibrary() {
        for (Class<?> violating : VIOLATING) {
            AssertionError error = assertThrows(AssertionError.class,
                () -> SecurityBoundaryRule.noCustomAuthImplementation().check(importing(violating)));
            for (Class<?> library : libraries()) {
                assertTrue(error.getMessage().contains(library.getName()), library + " in " + error.getMessage());
            }
        }
    }

    @Test
    @DisplayName("A class on the JWT list and its nested class pass the JWT rule, and only that rule")
    void listedJwtUserPassesItsRuleOnly() {
        JavaClasses classes = importing(ListedTokenValidator.class, ListedTokenValidator.Claims.class,
            CompliantPlatformService.class);

        assertDoesNotThrow(() -> SecurityBoundaryRule.noDirectJwtUsage().check(classes));
        AssertionError error = assertThrows(AssertionError.class,
            () -> SecurityBoundaryRule.noDirectCryptoUsage().check(classes));
        assertTrue(error.getMessage().contains(ListedTokenValidator.class.getName() + ".crypto()"), error.getMessage());
    }

    @Test
    @DisplayName("A class on the crypto list passes the crypto rule, and only that rule")
    void listedCryptoUserPassesItsRuleOnly() {
        JavaClasses classes = importing(ListedPasswordHasher.class, CompliantPlatformService.class);

        assertDoesNotThrow(() -> SecurityBoundaryRule.noDirectCryptoUsage().check(classes));
        AssertionError error = assertThrows(AssertionError.class,
            () -> SecurityBoundaryRule.noDirectJwtUsage().check(classes));
        assertTrue(error.getMessage().contains(ListedPasswordHasher.class.getName() + ".jwt()"), error.getMessage());
    }

    @Test
    @DisplayName("An unlisted class still fails in a module that lists another class")
    void unlistedClassNextToAListedOneFails() {
        Class<?> unlisted = VIOLATING.get(0);

        AssertionError error = assertThrows(AssertionError.class, () -> SecurityBoundaryRule.noDirectJwtUsage()
            .check(importing(ListedTokenValidator.class, unlisted)));
        assertTrue(error.getMessage().contains(unlisted.getName() + ".jwt()"), error.getMessage());
        assertFalse(error.getMessage().contains(ListedTokenValidator.class.getName()), error.getMessage());
    }

    @Test
    @DisplayName("Classes that use only the JDK pass every rule")
    void compliantClassesPass() {
        JavaClasses classes = importing(CompliantPlatformService.class, AlphaPrice.class);

        for (ArchRule rule : rules()) {
            assertDoesNotThrow(() -> rule.check(classes), rule.getDescription());
        }
    }

    @Test
    @DisplayName("A module without classes fails every rule rather than passing on nothing")
    void noClassFails(@TempDir Path empty) {
        JavaClasses none = new ClassFileImporter().importPath(empty);

        for (ArchRule rule : rules()) {
            assertThrows(AssertionError.class, () -> rule.check(none), rule.getDescription());
        }
    }

    /** The rule refuses both violating classes, names its own library and no other rule's. */
    private static void assertRejectsOnly(ArchRule rule, Class<?> library) {
        for (Class<?> violating : VIOLATING) {
            AssertionError error = assertThrows(AssertionError.class, () -> rule.check(importing(violating)));
            assertTrue(error.getMessage().contains(violating.getName()), error.getMessage());
            for (Class<?> each : libraries()) {
                assertTrue(error.getMessage().contains(each.getName()) == each.equals(library),
                    each + " in " + error.getMessage());
            }
        }
    }

    private static JavaClasses importing(Class<?>... classes) {
        return new ClassFileImporter().importClasses(classes);
    }

    private static Set<Class<?>> libraries() {
        return Set.of(Jwts.class, JWT.class, BCryptPasswordEncoder.class, OAuth2AuthorizedClient.class);
    }

    private static List<ArchRule> rules() {
        return List.of(SecurityBoundaryRule.noDirectJwtUsage(), SecurityBoundaryRule.noDirectAuth0Usage(),
            SecurityBoundaryRule.noDirectCryptoUsage(), SecurityBoundaryRule.noDirectOAuth2ClientUsage(),
            SecurityBoundaryRule.noCustomAuthImplementation());
    }
}
