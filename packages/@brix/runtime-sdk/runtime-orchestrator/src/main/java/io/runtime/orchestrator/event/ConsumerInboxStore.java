/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

/**
 * L2B boundary for inserting a Consumer Owner persistent Inbox receipt.
 *
 * <p>Implementations are Owner- or adapter-scoped and must insert the receipt
 * in the caller's active Consumer Owner transaction.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
@FunctionalInterface
public interface ConsumerInboxStore {

    /**
     * Inserts a processed receipt for the handler and message.
     *
     * @param handlerId stable manifest handler id
     * @param message canonical consumed message
     * @return {@code true} when this delivery is new; {@code false} when a
     * duplicate receipt already exists
     */
    boolean insertProcessed(String handlerId, CanonicalEventMessage message);
}
