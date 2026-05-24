/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.admin.service;

import io.runtime.sdk.capability.AuthFlowCapability;

/**
 * Platform-admin password policy shared by setup and self-service rotation.
 */
final class PlatformPasswordPolicy {

    private PlatformPasswordPolicy() {
    }

    static void requireCompliant(String password) {
        if (password == null
                || password.length() < 12
                || password.length() > 128
                || !containsLower(password)
                || !containsUpper(password)
                || !containsDigit(password)
                || !containsSymbol(password)) {
            throw new AuthFlowCapability.AuthFlowException(
                    AuthFlowCapability.AuthFlowException.CODE_PASSWORD_POLICY_VIOLATION,
                    "Password must be 12-128 characters and contain upper, lower, digit and symbol.");
        }
    }

    private static boolean containsLower(String value) {
        return value.chars().anyMatch(Character::isLowerCase);
    }

    private static boolean containsUpper(String value) {
        return value.chars().anyMatch(Character::isUpperCase);
    }

    private static boolean containsDigit(String value) {
        return value.chars().anyMatch(Character::isDigit);
    }

    private static boolean containsSymbol(String value) {
        return value.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
    }
}
