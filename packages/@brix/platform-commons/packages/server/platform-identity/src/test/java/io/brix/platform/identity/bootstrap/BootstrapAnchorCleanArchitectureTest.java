package io.brix.platform.identity.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.CommandLineRunner;

import io.brix.platform.identity.config.PlatformIdentityAutoConfiguration;

class BootstrapAnchorCleanArchitectureTest {

    @Test
    void autoConfigurationDoesNotRegisterStartupBootstrapRunner() {
        boolean runnerBean = Arrays.stream(PlatformIdentityAutoConfiguration.class.getDeclaredMethods())
                .map(Method::getReturnType)
                .anyMatch(type -> ApplicationRunner.class.isAssignableFrom(type)
                        || CommandLineRunner.class.isAssignableFrom(type));

        assertFalse(runnerBean);
    }

    @Test
    void bootstrapPropertiesStillExposeSecretBackedEndpointInputs() {
        SuperAdminBootstrapProperties properties = new SuperAdminBootstrapProperties();
        properties.setEnabled(true);
        properties.setSetupCode("setup-code");
        properties.setSetupBaseUrl("https://platform.example.invalid/platform/setup");

        assertNotNull(properties.getSetupCode());
        assertNotNull(properties.getSetupBaseUrl());
    }
}
