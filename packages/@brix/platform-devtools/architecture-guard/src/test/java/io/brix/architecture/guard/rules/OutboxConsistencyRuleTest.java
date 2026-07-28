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

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.importer.ClassFileImporter;

class OutboxConsistencyRuleTest {

    @Test
    @DisplayName("Plugin core dependencies on Outbox implementation packages fail")
    void pluginCoreOutboxImplementationDependencyFails() {
        var classes = new ClassFileImporter()
            .importPackages(
                "io.brix.architecture.guard.testfixtures.violating.outbox.core",
                "io.infra.adapter.outbox");

        assertThrows(AssertionError.class,
            () -> OutboxConsistencyRule.noDirectOutboxImplementationDependency().check(classes));
    }

    @Test
    @DisplayName("Plugin core dependencies on broker SDK packages fail")
    void pluginCoreBrokerSdkDependencyFails() {
        var classes = new ClassFileImporter()
            .importPackages(
                "io.brix.architecture.guard.testfixtures.violating.broker.core",
                "org.apache.kafka.clients.producer");

        assertThrows(AssertionError.class,
            () -> OutboxConsistencyRule.noBrokerSdkDependencies().check(classes));
    }
}
