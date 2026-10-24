/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.identity.security;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import io.runtime.sdk.capability.IdentityAccountCapability.PasswordRejection;

/**
 * Checks a password an identity is about to set (identity-auth {@code BRX-IA-CRED-001}).
 *
 * <p>Staff always sign in with a second factor, so a password needs at least 8 characters,
 * counted as Unicode code points. It must not be on the list of passwords known from breaches,
 * be one repeated character or a straight run of digits or letters, or contain the account name
 * or the service name. There are no composition rules and no expiry.</p>
 *
 * <p>The list holds the passwords of 8 to 64 characters among the 100,000 most common ones of
 * Have I Been Pwned, lower-cased; the check ignores case. Source and licences are in
 * {@code META-INF/brix/identity/breached-passwords.NOTICE}.</p>
 */
public final class PasswordPolicy {

    /** Shortest password of a multi-factor sign-in ({@code BRX-IA-CRED-001}). */
    public static final int MIN_LENGTH_WITH_SECOND_FACTOR = 8;
    public static final int DEFAULT_MAX_LENGTH = 128;
    static final String BREACHED_PASSWORDS = "META-INF/brix/identity/breached-passwords.txt";
    private static final int MIN_RUN = 8;
    private static final int MIN_ACCOUNT_NAME = 4;
    private static final String SERVICE_NAME = "brix";

    private final int minLength;
    private final int maxLength;
    private final Set<String> breached;

    public PasswordPolicy() {
        this(MIN_LENGTH_WITH_SECOND_FACTOR, DEFAULT_MAX_LENGTH);
    }

    /**
     * Creates the policy with the length limits of the security profile; the list is read now, so
     * a missing list fails at startup rather than accepting every password.
     *
     * @param minLength shortest password, at least {@value #MIN_LENGTH_WITH_SECOND_FACTOR}
     * @param maxLength longest password, at least 64 so that passphrases fit
     */
    public PasswordPolicy(int minLength, int maxLength) {
        if (minLength < MIN_LENGTH_WITH_SECOND_FACTOR) {
            throw new IllegalArgumentException(
                    "passwords need at least " + MIN_LENGTH_WITH_SECOND_FACTOR + " characters (BRX-IA-CRED-001)");
        }
        if (maxLength < Math.max(64, minLength)) {
            throw new IllegalArgumentException("the longest password must allow at least 64 characters");
        }
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.breached = readList();
    }

    /**
     * Checks a new password.
     *
     * @param password new password
     * @param email the identity's email, whose local part counts as its account name
     * @param username the identity's username
     * @return empty when the password is acceptable, otherwise why it is not
     */
    public Optional<PasswordRejection> check(String password, String email, String username) {
        if (password == null) {
            return Optional.of(PasswordRejection.TOO_SHORT);
        }
        int length = password.codePointCount(0, password.length());
        if (length < minLength) {
            return Optional.of(PasswordRejection.TOO_SHORT);
        }
        if (length > maxLength) {
            return Optional.of(PasswordRejection.TOO_LONG);
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (breached.contains(lower) || isTrivial(lower)) {
            return Optional.of(PasswordRejection.BREACHED);
        }
        if (containsName(lower, localPart(email)) || containsName(lower, username)
                || lower.contains(SERVICE_NAME)) {
            return Optional.of(PasswordRejection.CONTAINS_ACCOUNT_NAME);
        }
        return Optional.empty();
    }

    /** One repeated character, or digits or letters that only count up or down, such as 98765432. */
    static boolean isTrivial(String lower) {
        int[] points = lower.codePoints().toArray();
        if (points.length < MIN_RUN) {
            return false;
        }
        boolean same = true;
        boolean up = true;
        boolean down = true;
        boolean alphanumeric = isAsciiAlphanumeric(points[0]);
        for (int i = 1; i < points.length; i++) {
            int step = points[i] - points[i - 1];
            same &= step == 0;
            up &= step == 1;
            down &= step == -1;
            alphanumeric &= isAsciiAlphanumeric(points[i]);
        }
        return same || (alphanumeric && (up || down));
    }

    private static boolean isAsciiAlphanumeric(int point) {
        return (point >= '0' && point <= '9') || (point >= 'a' && point <= 'z');
    }

    private static boolean containsName(String lowerPassword, String name) {
        if (name == null) {
            return false;
        }
        String value = name.trim().toLowerCase(Locale.ROOT);
        return value.length() >= MIN_ACCOUNT_NAME && lowerPassword.contains(value);
    }

    private static String localPart(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private static Set<String> readList() {
        ClassLoader loader = PasswordPolicy.class.getClassLoader();
        try (InputStream in = loader.getResourceAsStream(BREACHED_PASSWORDS)) {
            if (in == null) {
                throw new IllegalStateException("The breached password list " + BREACHED_PASSWORDS + " is missing");
            }
            Set<String> words = new HashSet<>(65_536);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    if (!line.isEmpty()) {
                        words.add(line);
                    }
                }
            }
            if (words.isEmpty()) {
                throw new IllegalStateException("The breached password list " + BREACHED_PASSWORDS + " is empty");
            }
            return Set.copyOf(words);
        } catch (IOException e) {
            throw new IllegalStateException("The breached password list could not be read", e);
        }
    }
}
