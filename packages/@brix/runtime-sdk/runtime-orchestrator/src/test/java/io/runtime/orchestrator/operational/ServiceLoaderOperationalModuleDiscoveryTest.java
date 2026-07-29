/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.operational;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import org.junit.jupiter.api.Test;

class ServiceLoaderOperationalModuleDiscoveryTest {

    @Test
    void discoversOneProviderAndAssociatesItsSameArtifactDescriptor() {
        var discovered = new ServiceLoaderOperationalModuleDiscovery().discover();

        assertEquals(1, discovered.size());
        assertEquals("runtime-test-operational", discovered.get(0).descriptor().identity().moduleId());
        assertEquals(ServiceLoadedTestOperationalModule.class, discovered.get(0).provider().getClass());
    }

    @Test
    void treatsSpringBootNestedJarDescriptorAsSameArtifact()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = ServiceLoaderOperationalModuleDiscovery.class.getDeclaredMethod(
            "belongsToCodeSource", URL.class, URL.class);
        method.setAccessible(true);

        URL codeSource = url("jar:nested:/app/app.jar/!BOOT-INF/lib/platform-admin-3.2.0.jar!/");
        URL descriptor = url("jar:nested:/app/app.jar/!BOOT-INF/lib/platform-admin-3.2.0.jar!/META-INF/brix/platform-operational.yaml");

        assertTrue((Boolean) method.invoke(null, descriptor, codeSource));
    }

    private static URL url(String value) {
        try {
            return new URL(null, value, new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL url) throws IOException {
                    throw new IOException("Connection is not available for synthetic test URLs");
                }
            });
        } catch (java.net.MalformedURLException e) {
            throw new IllegalArgumentException(value, e);
        }
    }
}
