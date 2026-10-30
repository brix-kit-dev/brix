/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.runtime.orchestrator.persistence;

import java.util.Optional;

import io.runtime.sdk.capability.CommandCapability;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.PersistenceCapability;

/**
 * L2B SPI through which the persistence adapter supplies owner-scoped persistence.
 *
 * <p>{@link PersistenceCapability} is not a global capability: every instance is bound to exactly
 * one data owner (root blueprint BRX-RS-DATA-026 and BRX-RS-DATA-027). The L2C persistence adapter
 * implements this SPI, and the runtime uses it to hand the right instance to a module's
 * {@code PluginContext} or internal-contract provider context and to open the owner's unit of work
 * around runtime entries (BRX-RS-DATA-028, BRX-RS-HOST-020).</p>
 *
 * <p>The module id passed in is always derived by the runtime from a validated descriptor; it is
 * never self-reported by plugin code.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
@FunctionalInterface
public interface OwnerPersistenceProvider {

    /**
     * Returns the persistence capability of the given data owner.
     *
     * @param ownerModuleId runtime module id of the data owner
     * @return the owner-scoped capability, or empty when the module does not declare
     *         {@link PersistenceCapability} or no persistence adapter is assembled
     */
    Optional<PersistenceCapability> forOwner(String ownerModuleId);

    /**
     * Returns the outbox event bus of an owner whose manifest declares {@code data.outbox}: it writes the
     * owner's CRITICAL and STANDARD events to that outbox in the owner's own transaction (root
     * BRX-RS-MESSAGE-017, MESSAGE-022; P4 design D13).
     *
     * @param ownerModuleId owner plugin id
     * @return the owner's outbox event bus, or empty when the owner publishes through no outbox
     */
    default Optional<EventBusCapability> eventBusFor(String ownerModuleId) {
        return Optional.empty();
    }

    /**
     * Returns the owner's command capability, which writes its commands to its own outbox (root
     * BRX-RS-MESSAGE-011; Kafka design §14 K22).
     *
     * @param ownerModuleId owner module id
     * @return the owner's command capability, or empty when the owner declares none
     */
    default Optional<CommandCapability> commandBusFor(String ownerModuleId) {
        return Optional.empty();
    }

    /**
     * Refuses an Owner whose canonical messaging tables (the {@code data.outbox} and {@code data.inbox} its
     * manifest declares) do not match the platform schema (root BRX-RS-MESSAGE-026, Kafka design §14 K19).
     * The runtime calls it before the plugin starts, so a missing or incomplete table fails the plugin by its
     * required/optional rules instead of its first message. The default checks nothing.
     *
     * @param ownerModuleId owner module id
     * @throws RuntimeException when a declared table does not conform
     */
    default void requireMessagingTables(String ownerModuleId) {
    }

    /**
     * Returns a provider used when no persistence adapter is assembled.
     *
     * @return a provider that never supplies a capability
     */
    static OwnerPersistenceProvider none() {
        return ownerModuleId -> Optional.empty();
    }
}
