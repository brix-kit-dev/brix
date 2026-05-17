package io.runtime.orchestrator.endpoint;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.runtime.orchestrator.manifest.UIManifestLoader;
import io.runtime.orchestrator.registry.ModuleRegistry;
import io.runtime.sdk.capability.LifecycleCapability;
import io.runtime.sdk.capability.ModuleMetadata;

class PluginRegistryEndpointTest {

    @Test
    void registeredModuleWithoutRemoteEntryIsDisabledForWebDiscovery() throws Exception {
        ModuleRegistry registry = mock(ModuleRegistry.class);
        UIManifestLoader manifestLoader = mock(UIManifestLoader.class);
        LifecycleCapability module = module("brix-app-identity", "Identity", 10);
        Map<String, Object> manifest = manifest(Map.of(
                "enabled", true,
                "manifestUrl", "/plugins/identity/ui-manifest.json",
                "scope", "identity"));

        when(registry.getByStartupOrder()).thenReturn(List.of(module));
        when(manifestLoader.getManifest("brix-app-identity")).thenReturn(manifest);
        when(manifestLoader.getAllManifests()).thenReturn(Map.of());

        PluginRegistryEndpoint endpoint = endpoint(registry, manifestLoader);

        PluginRegistryEndpoint.PluginsResponse response = endpoint.getPlugins();

        assertEquals(1, response.plugins().size());
        PluginRegistryEndpoint.PluginInfo plugin = response.plugins().get(0);
        assertEquals("brix-app-identity", plugin.id());
        assertFalse(plugin.enabled());
        assertEquals("/plugins/brix-app-identity/remoteEntry.js", plugin.remoteEntry());
        assertEquals("/plugins/identity/ui-manifest.json", plugin.manifestUrl());
    }

    @Test
    void registeredModuleWithRemoteEntryAndScopeIsEnabledForWebDiscovery() throws Exception {
        ModuleRegistry registry = mock(ModuleRegistry.class);
        UIManifestLoader manifestLoader = mock(UIManifestLoader.class);
        LifecycleCapability module = module("brix-app-booking", "Booking", 20);
        Map<String, Object> manifest = manifest(Map.of(
                "enabled", true,
                "remoteEntry", "/plugins/booking/remoteEntry.js",
                "manifestUrl", "/plugins/booking/ui-manifest.json",
                "scope", "booking"));

        when(registry.getByStartupOrder()).thenReturn(List.of(module));
        when(manifestLoader.getManifest("brix-app-booking")).thenReturn(manifest);
        when(manifestLoader.getAllManifests()).thenReturn(Map.of());

        PluginRegistryEndpoint endpoint = endpoint(registry, manifestLoader);

        PluginRegistryEndpoint.PluginsResponse response = endpoint.getPlugins();

        assertEquals(1, response.plugins().size());
        PluginRegistryEndpoint.PluginInfo plugin = response.plugins().get(0);
        assertEquals("brix-app-booking", plugin.id());
        assertTrue(plugin.enabled());
        assertEquals("/plugins/booking/remoteEntry.js", plugin.remoteEntry());
        assertEquals("/plugins/booking/ui-manifest.json", plugin.manifestUrl());
    }

    private PluginRegistryEndpoint endpoint(
            ModuleRegistry registry,
            UIManifestLoader manifestLoader) throws Exception {
        PluginRegistryEndpoint endpoint = new PluginRegistryEndpoint(registry, manifestLoader);
        setField(endpoint, "hostMode", "product");
        setField(endpoint, "pluginsBaseUrl", "/plugins");
        return endpoint;
    }

    private LifecycleCapability module(String moduleId, String moduleName, int startupOrder) {
        LifecycleCapability module = mock(LifecycleCapability.class);
        ModuleMetadata metadata = ModuleMetadata.builder()
                .moduleId(moduleId)
                .moduleName(moduleName)
                .version("1.0.0")
                .startupOrder(startupOrder)
                .build();
        when(module.getMetadata()).thenReturn(metadata);
        return module;
    }

    private Map<String, Object> manifest(Map<String, Object> web) {
        return Map.of("ui", Map.of("web", web));
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}