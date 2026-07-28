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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.ApplicationContext;

import io.runtime.orchestrator.endpoint.RuntimeShellEndpointController;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.ObservabilityCapability;

class CapabilityAutoConfigurationTest {

    @Test
    void capabilityRegistryRunsAfterCurrentAdapterAutoConfigurations() {
        AutoConfiguration annotation = CapabilityAutoConfiguration.class.getAnnotation(AutoConfiguration.class);
        List<String> afterNames = Arrays.asList(annotation.afterName());

        assertTrue(afterNames.contains("io.infra.adapter.kafka.config.KafkaAutoConfiguration"));
        assertTrue(afterNames.contains("io.infra.adapter.redis.config.RedisAutoConfiguration"));
        assertFalse(afterNames.contains("io.infra.adapter.kafka.autoconfigure.KafkaAdapterAutoConfiguration"));
        assertFalse(afterNames.contains("io.infra.adapter.redis.autoconfigure.RedisAdapterAutoConfiguration"));
    }

    @Test
    void pluginCapabilityVerifierAcceptsSimpleAndFullyQualifiedCapabilityNames()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method collector = PluginRegistryAutoConfiguration.class.getDeclaredMethod(
                "collectCapabilitySupertypes", Class.class, Set.class);
        collector.setAccessible(true);

        Set<String> identifiers = new HashSet<>();
        collector.invoke(null, EventBusCapability.class, identifiers);

        assertTrue(identifiers.contains("EventBusCapability"));
        assertTrue(identifiers.contains("io.runtime.sdk.capability.EventBusCapability"));
    }

    @Test
    void runtimeShellEndpointControllerAllowsSpringInfrastructureProxying() {
        assertFalse(Modifier.isFinal(RuntimeShellEndpointController.class.getModifiers()));
    }

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
