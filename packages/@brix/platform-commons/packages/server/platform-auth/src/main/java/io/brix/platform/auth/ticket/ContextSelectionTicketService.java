/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.auth.ticket;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;

import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and consumes one-time context selection tickets.
 *
 * <p>Tickets are high-entropy bearer values. Only a SHA-256 hash is stored, and
 * each ticket is bound to an Identity Token {@code jti} and consumed in a
 * transaction.</p>
 *
 * @since 3.2.2
 */
public class ContextSelectionTicketService {

    private static final int TICKET_BYTES = 32;
    private static final long TTL_SECONDS = 300L;

    private final ContextSelectionTicketRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ContextSelectionTicketService(ContextSelectionTicketRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public String issue(Long identityId, String identityTokenJti, String roleType,
                        Long tenantId, Long refId, String contextId) {
        if (identityId == null || identityTokenJti == null || identityTokenJti.isBlank()
                || roleType == null || roleType.isBlank() || tenantId == null
                || refId == null || contextId == null || contextId.isBlank()) {
            throw new IllegalArgumentException("context selection ticket fields are required");
        }
        String ticket = randomTicket();
        OffsetDateTime now = OffsetDateTime.now();
        ContextSelectionTicket entity = new ContextSelectionTicket(
                generateId(),
                hash(ticket),
                identityId,
                identityTokenJti,
                roleType,
                tenantId,
                refId,
                contextId,
                now,
                now.plusSeconds(TTL_SECONDS));
        repository.save(entity);
        return ticket;
    }

    @Transactional
    public Selection consume(String ticket, Long identityId, String identityTokenJti) {
        if (ticket == null || ticket.isBlank() || identityId == null
                || identityTokenJti == null || identityTokenJti.isBlank()) {
            throw new InvalidTicketException("Context selection ticket is required");
        }
        OffsetDateTime now = OffsetDateTime.now();
        ContextSelectionTicket entity = repository.findByTicketHash(hash(ticket))
                .orElseThrow(() -> new InvalidTicketException("Context selection ticket is invalid"));
        if (!entity.isActiveAt(now)
                || !identityId.equals(entity.getIdentityId())
                || !identityTokenJti.equals(entity.getIdentityTokenJti())) {
            throw new InvalidTicketException("Context selection ticket is invalid");
        }
        entity.consume(now);
        repository.save(entity);
        return new Selection(entity.getRoleType(), entity.getTenantId(),
                entity.getRefId(), entity.getContextId());
    }

    private String randomTicket() {
        byte[] bytes = new byte[TICKET_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String ticket) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(ticket.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static long generateId() {
        return System.nanoTime() ^ (Thread.currentThread().getId() << 32);
    }

    public record Selection(String roleType, Long tenantId, Long refId, String contextId) {}

    public static class InvalidTicketException extends RuntimeException {
        public InvalidTicketException(String message) {
            super(message);
        }
    }
}
