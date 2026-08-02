/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.auth.servlet;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import io.brix.platform.auth.jwt.JwtValidator;
import io.runtime.sdk.capability.AuthCapability;

class SecurityServletAutoConfigurationTest {

    @Test
    void registersSecurityContextFilterWithJwtValidatorInServletRuntime(@TempDir Path tempDir) throws Exception {
        TestKeys keys = writeKeys(tempDir);

        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SecurityServletAutoConfiguration.class))
                .withPropertyValues(
                        "platform.security.jwt.private-key-path=" + keys.privateKeyPath().toUri(),
                        "platform.security.jwt.public-key-path=" + keys.publicKeyPath().toUri())
                .run(context -> {
                    assertThat(context).hasSingleBean(JwtValidator.class);
                    assertThat(context).hasSingleBean(AuthCapability.class);
                    assertThat(context).hasBean("securityContextFilter");
                    assertThat(context.getBean("securityContextFilter"))
                            .isInstanceOf(FilterRegistrationBean.class);
                });
    }

    private static TestKeys writeKeys(Path tempDir) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        Path privateKeyPath = tempDir.resolve("jwt-private.pem");
        Files.writeString(
                privateKeyPath,
                "-----BEGIN PRIVATE KEY-----\n"
                        + Base64.getMimeEncoder(64, new byte[] {'\n'})
                                .encodeToString(keyPair.getPrivate().getEncoded())
                        + "\n-----END PRIVATE KEY-----\n",
                StandardCharsets.US_ASCII);

        Path publicKeyPath = tempDir.resolve("jwt-public.pem");
        Files.writeString(
                publicKeyPath,
                "-----BEGIN PUBLIC KEY-----\n"
                        + Base64.getMimeEncoder(64, new byte[] {'\n'})
                                .encodeToString(keyPair.getPublic().getEncoded())
                        + "\n-----END PUBLIC KEY-----\n",
                StandardCharsets.US_ASCII);

        return new TestKeys(privateKeyPath, publicKeyPath);
    }

    private record TestKeys(Path privateKeyPath, Path publicKeyPath) {
    }
}
