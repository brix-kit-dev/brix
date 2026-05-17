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
package io.runtime.orchestrator.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

import io.runtime.sdk.capability.ObservabilityCapability;

class CapabilityAutoConfigurationTest {

    @Test
    void requiredCapabilityWithMissingClassFailsStartup() {
        CapabilityProperties properties = new CapabilityProperties();
        properties.setAutoDiscovery(false);
        properties.setValidateOnStartup(true);
        properties.setRequired(List.of("io.brix.missing.RequiredCapability"));

        CapabilityAutoConfiguration autoConfiguration = new CapabilityAutoConfiguration(properties);

        assertThrows(IllegalStateException.class,
                () -> autoConfiguration.capabilityRegistry(applicationContext(), observabilityProvider()));
    }

    @Test
    void optionalCapabilityWithMissingClassDoesNotFailStartup() {
        CapabilityProperties properties = new CapabilityProperties();
        properties.setAutoDiscovery(false);
        properties.setValidateOnStartup(true);
        properties.setOptional(List.of("io.brix.missing.OptionalCapability"));

        CapabilityAutoConfiguration autoConfiguration = new CapabilityAutoConfiguration(properties);

        assertDoesNotThrow(
                () -> autoConfiguration.capabilityRegistry(applicationContext(), observabilityProvider()));
    }

    private ApplicationContext applicationContext() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBeansOfType(any())).thenReturn(Map.of());
        when(applicationContext.getBeansOfType(eq(ObservabilityCapability.class))).thenReturn(Map.of());
        when(applicationContext.getBeansWithAnnotation(any())).thenReturn(Map.of());
        when(applicationContext.getBeanNamesForAnnotation(any())).thenReturn(new String[0]);
        return applicationContext;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<List<ObservabilityCapability>> observabilityProvider() {
        ObjectProvider<List<ObservabilityCapability>> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(List.of());
        return provider;
    }
}
