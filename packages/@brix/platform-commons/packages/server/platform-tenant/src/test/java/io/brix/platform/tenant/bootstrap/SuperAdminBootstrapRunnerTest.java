package io.brix.platform.tenant.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.brix.platform.tenant.config.TenantAutoConfiguration;

class SuperAdminBootstrapRunnerTest {

    @Test
    void autoConfigurationDoesNotRegisterStartupBootstrapRunner() {
        assertFalse(Arrays.stream(TenantAutoConfiguration.class.getDeclaredMethods())
                .anyMatch(method -> method.getReturnType().equals(SuperAdminBootstrapRunner.class)));
    }

    @Test
    void bootstrapPropertiesStillExposeSecretBackedInputs() {
        SuperAdminBootstrapProperties properties = new SuperAdminBootstrapProperties();
        properties.setSetupCode("setup-code");
        properties.setSetupBaseUrl("https://platform.example.invalid/platform/setup");

        assertNotNull(properties.getSetupCode());
        assertNotNull(properties.getSetupBaseUrl());
    }
}
