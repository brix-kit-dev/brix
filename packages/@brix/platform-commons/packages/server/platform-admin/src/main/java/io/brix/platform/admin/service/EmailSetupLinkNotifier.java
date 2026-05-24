/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.brix.platform.admin.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import io.brix.platform.admin.config.PlatformAdminSetupProperties;
import io.runtime.sdk.capability.NotificationCapability;

/** SMTP-backed setup-link delivery. */
public class EmailSetupLinkNotifier implements NotificationCapability {

    private final JavaMailSender mailSender;
    private final PlatformAdminSetupProperties setupProperties;

    public EmailSetupLinkNotifier(JavaMailSender mailSender, PlatformAdminSetupProperties setupProperties) {
        this.mailSender = mailSender;
        this.setupProperties = setupProperties;
    }

    @Override
    public void sendSetupLink(String email, String setupUrl, String purpose) {
        String from = trimToNull(setupProperties.getMailFrom());
        if (from == null) {
            throw new IllegalStateException("brix.platform.admin.setup.mail-from is required for setup-link delivery");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Complete your Brix platform admin setup");
        message.setText("Hello,\n\n"
                + "Use this link to set your password and bind MFA:\n"
                + setupUrl + "\n\n"
                + "Purpose: " + purpose + "\n"
                + "This link is single-use and expires shortly.");
        mailSender.send(message);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
