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

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * NoDirectHttpClientsRule Test Cases
 *
 * <p>Tests for Rule 3 enforcement: No direct RestTemplate/WebClient/OpenFeign/OkHttp usage.</p>
 *
 * <h2>Covered HTTP Clients</h2>
 * <ul>
 *   <li>Spring RestTemplate</li>
 *   <li>Spring WebClient (reactive)</li>
 *   <li>Spring Cloud OpenFeign</li>
 *   <li>OkHttp</li>
 * </ul>
 *
 * @author Brix Architecture Team
 * @since 3.1.0
 */
@DisplayName("Rule 3: No Direct HTTP Clients")
class NoDirectHttpClientsRuleTest {

    // ============================================================================
    // RestTemplate Rule Tests
    // ============================================================================

    @Nested
    @DisplayName("RestTemplate Checks")
    class RestTemplateTests {

        @Test
        @DisplayName("Guard library should pass RestTemplate checks")
        void guardLibraryShouldPass() {
            ArchRule rule = NoDirectHttpClientsRule.noRestTemplate();
            
            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                rule.check(classes);
            }, "Guard library should not use RestTemplate");
        }
    }

    // ============================================================================
    // WebClient Rule Tests
    // ============================================================================

    @Nested
    @DisplayName("WebClient Checks")
    class WebClientTests {

        @Test
        @DisplayName("Guard library should pass WebClient checks")
        void guardLibraryShouldPass() {
            ArchRule rule = NoDirectHttpClientsRule.noWebClient();
            
            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                rule.check(classes);
            }, "Guard library should not use WebClient");
        }
    }

    // ============================================================================
    // OpenFeign Rule Tests
    // ============================================================================

    @Nested
    @DisplayName("OpenFeign Checks")
    class OpenFeignTests {

        @Test
        @DisplayName("Guard library should pass OpenFeign checks")
        void guardLibraryShouldPass() {
            ArchRule rule = NoDirectHttpClientsRule.noOpenFeign();
            
            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                rule.check(classes);
            }, "Guard library should not use OpenFeign");
        }
    }

    // ============================================================================
    // OkHttp Rule Tests
    // ============================================================================

    @Nested
    @DisplayName("OkHttp Checks")
    class OkHttpTests {

        @Test
        @DisplayName("Guard library should pass OkHttp checks")
        void guardLibraryShouldPass() {
            ArchRule rule = NoDirectHttpClientsRule.noOkHttp();
            
            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                rule.check(classes);
            }, "Guard library should not use OkHttp");
        }
    }
}
