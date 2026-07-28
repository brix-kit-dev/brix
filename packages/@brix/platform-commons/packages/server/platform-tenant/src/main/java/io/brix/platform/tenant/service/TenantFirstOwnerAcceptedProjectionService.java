/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.service;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

import io.brix.platform.tenant.entity.PlatformTenantInbox;
import io.brix.platform.tenant.entity.PlatformTenantInboxId;
import io.brix.platform.tenant.event.TenantFirstOwnerAcceptedEvent;
import io.brix.platform.tenant.repository.PlatformTenantInboxRepository;

/**
 * Consumer Owner handler for {@code TenantFirstOwnerAccepted}.
 *
 * <p>The method inserts the canonical Inbox receipt and writes the first-owner
 * projection in one {@code platform-tenant} local transaction. A duplicate
 * {@code (handlerId,messageId)} receipt returns without invoking the side
 * effect, which is the durable idempotency boundary for broker retries.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public class TenantFirstOwnerAcceptedProjectionService {

    public static final String HANDLER_ID = "tenant-first-owner-projection.v1";
    public static final String SCHEMA_VERSION = "1.0.0";

    private final PlatformTenantInboxRepository inboxRepository;
    private final FirstOwnerProjectionWriter projectionWriter;
    private final ConcurrentMap<String, ReentrantLock> messageLocks = new ConcurrentHashMap<>();

    /**
     * Creates the Consumer handler service.
     *
     * @param inboxRepository canonical Inbox repository
     * @param projectionWriter projection side-effect writer
     */
    public TenantFirstOwnerAcceptedProjectionService(
            PlatformTenantInboxRepository inboxRepository,
            FirstOwnerProjectionWriter projectionWriter) {
        this.inboxRepository = Objects.requireNonNull(inboxRepository, "inboxRepository must not be null");
        this.projectionWriter = Objects.requireNonNull(projectionWriter, "projectionWriter must not be null");
    }

    /**
     * Handles one delivered FIRST_OWNER accepted event.
     *
     * @param event delivered event
     * @return {@code true} when a new side effect was written; {@code false}
     * when this was a duplicate delivery
     */
    @Transactional
    public boolean handle(TenantFirstOwnerAcceptedEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        return handle(event.getEventId(), event);
    }

    /**
     * Handles one delivered FIRST_OWNER accepted event with an explicit
     * canonical message id from the broker envelope.
     *
     * @param messageId canonical message id
     * @param event delivered event
     * @return {@code true} when a new side effect was written
     */
    @Transactional
    public boolean handle(String messageId, TenantFirstOwnerAcceptedEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        requireText(messageId, "messageId");
        ReentrantLock lock = messageLocks.computeIfAbsent(messageId, ignored -> new ReentrantLock());
        lock.lock();
        boolean releaseAfterTransaction = registerLockRelease(messageId, lock);
        try {
            PlatformTenantInboxId inboxId = new PlatformTenantInboxId(HANDLER_ID, messageId);
            if (inboxRepository.existsById(inboxId)) {
                return false;
            }
            try {
                PlatformTenantInbox inbox = PlatformTenantInbox.processedEvent(
                    HANDLER_ID,
                    messageId,
                    TenantFirstOwnerAcceptedEvent.EVENT_TYPE,
                    SCHEMA_VERSION,
                    event.tenantIdValue());
                inboxRepository.saveAndFlush(inbox);
            } catch (DataIntegrityViolationException ex) {
                return false;
            }
            projectionWriter.write(messageId, event);
            return true;
        } finally {
            if (!releaseAfterTransaction) {
                releaseLock(messageId, lock);
            }
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private boolean registerLockRelease(String messageId, ReentrantLock lock) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                releaseLock(messageId, lock);
            }
        });
        return true;
    }

    private void releaseLock(String messageId, ReentrantLock lock) {
        try {
            lock.unlock();
        } finally {
            if (!lock.hasQueuedThreads()) {
                messageLocks.remove(messageId, lock);
            }
        }
    }
}
