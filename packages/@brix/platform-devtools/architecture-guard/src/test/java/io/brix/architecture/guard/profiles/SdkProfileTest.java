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
package io.brix.architecture.guard.profiles;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.brix.samples.fixturebeta.core.BetaStock;
import io.runtime.sdk.fixture.SdkUsesPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the SDK profile's plugin rule checks the real plugin roots.
 */
class SdkProfileTest {

    @Test
    @DisplayName("An SDK class that uses a plugin fails")
    void pluginDependencyFails() {
        AssertionError error = assertThrows(AssertionError.class,
            () -> SdkProfile.sdkNotDependOnPlugins.check(new ClassFileImporter().importClasses(SdkUsesPlugin.class)));
        assertTrue(error.getMessage().contains(BetaStock.class.getName()), error.getMessage());
    }
}
