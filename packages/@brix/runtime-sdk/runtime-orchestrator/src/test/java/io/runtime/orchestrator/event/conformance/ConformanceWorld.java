/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event.conformance;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import tools.jackson.databind.ObjectMapper;

import io.runtime.sdk.plugin.CommandInvocation;
import io.runtime.sdk.plugin.CommandHandler;
import io.runtime.orchestrator.plugin.PluginRuntimeManager;
import io.runtime.orchestrator.command.CommandProviders;
import io.runtime.orchestrator.command.CommandMessages;
import io.runtime.orchestrator.command.CommandPolicies;
import io.runtime.orchestrator.command.CommandDelivery;
import io.runtime.orchestrator.command.CommandRecord;
import io.runtime.orchestrator.command.CommandStatusReader;
import io.runtime.orchestrator.command.OwnerCommandIdempotency;
import io.runtime.orchestrator.event.EventSubscriptionTransport;
import io.runtime.sdk.command.CommandStatus;
import io.runtime.orchestrator.event.EventDelivery;
import io.runtime.orchestrator.event.EventInboxStore;
import io.runtime.orchestrator.event.EventSubscribers;
import io.runtime.orchestrator.outbox.CanonicalMessagingTables;
import io.runtime.orchestrator.outbox.OutboxEndpoint;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxRelayPolicy;
import io.runtime.orchestrator.persistence.PolicyAuditWriter;
import io.runtime.orchestrator.persistence.PolicyEventDeliveryScopes;
import io.runtime.orchestrator.persistence.PolicyUseRecord;
import io.runtime.orchestrator.persistence.TenantAccessPolicy;
import io.runtime.orchestrator.persistence.TenantScope;
import io.runtime.orchestrator.persistence.TenantScopes;
import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.orchestrator.plugin.PluginRuntimeManager.BoundEventHandler;
import io.runtime.sdk.capability.FirstOwnerInviteeIdentitySetupCapability;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.capability.PersistenceCapabilityException;
import io.runtime.sdk.capability.UnitOfWork;
import io.runtime.sdk.error.BrixException;
import io.runtime.sdk.error.ErrorCategory;
import io.runtime.sdk.error.ErrorCode;
import io.runtime.sdk.event.identity.FirstOwnerInviteeSetupCompletedEvent;
import io.runtime.sdk.plugin.EventHandler;
import io.runtime.sdk.plugin.EventInvocation;

/**
 * What every transport under the conformance suite delivers into: a publisher's outbox, one subscription,
 * the subscriber owner's unit of work and persistent inbox, and a handler that can be made to fail.
 * <p>The unit of work commits the inbox record and what the handler did together, or neither; a second
 * commit of the same {@code (handlerId, eventId)} fails as the inbox's unique key would (root
 * BRX-RS-MESSAGE-025). {@link #withoutInbox()} makes a world whose subscription keeps no inbox.</p>
 */
public final class ConformanceWorld {

    public static final String EVENT = "MemberAccepted";
    public static final String PUBLISHER = "conformance-publisher";
    public static final String SUBSCRIBER = "conformance-subscriber";
    public static final String HANDLER = "member-accepted-projection.v1";
    public static final long TENANT = 910001L;
    /** The publisher's outbox table, as the management endpoint names it. */
    public static final String OUTBOX = "conformance_publisher_outbox";
    public static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    /** The command the publisher submits and the subscriber provides (Kafka design §14 K22). */
    public static final String COMMAND = "ReserveStock";
    public static final String COMMAND_HANDLER = "reserve-stock.v1";
    /** The code of every stable failure the command handler is made to throw (standard-link plan point 9). */
    public static final String STOCK_REFUSED = "conformance.stock_refused";

    /**
     * A PLATFORM fact the subscriber takes under the registered cross-tenant policy
     * {@code first-owner-invitee-setup-fanout} (multi-tenant §6.4, P-3).
     */
    public static final String SETUP_EVENT = "FirstOwnerInviteeSetupCompleted";
    /** The plugin that publishes {@link #SETUP_EVENT}. */
    public static final String IDENTITY_PUBLISHER = "conformance-identity";
    /** The subscriber's handler of {@link #SETUP_EVENT}. */
    public static final String SETUP_HANDLER = "invitee-setup-fanout.v1";
    /** The e-mail the identity source gives every invitee: the key of the policy scope. */
    public static final String INVITEE_EMAIL = "invitee@conformance.test";
    /** The tenants the fan-out reaches, as the persistence adapter would report them. */
    public static final List<Long> FANOUT_TENANTS = List.of(910001L, 910002L);

    /** The payload of {@link #COMMAND}. */
    public record Reserve(String sku) {
    }

    /**
     * Payload the subscriber's handler declares.
     *
     * @param tenantId tenant of the member
     * @param memberId the member
     */
    public record MemberAccepted(Long tenantId, Long memberId) {
    }

    /**
     * An event the handler committed.
     *
     * @param eventId the event id
     * @param memberId the payload's member
     * @param scope the tenant scope the unit of work bound
     * @param traceparent the trace context the handler received
     * @param attempt the delivery attempt
     */
    public record Handled(String eventId, Long memberId, TenantScope scope, String traceparent, int attempt) {
    }

    private final InMemoryOutboxStore outbox = new InMemoryOutboxStore();
    private final RecordingMessageAudit audit = new RecordingMessageAudit();
    private final List<PolicyUseRecord> policyUses = new CopyOnWriteArrayList<>();
    private final PolicyAuditWriter policyAudit = new PolicyAuditWriter() {
        @Override
        public boolean covers(String action) {
            return TenantAccessPolicy.FIRST_OWNER_INVITEE_SETUP_FANOUT.auditAction().orElseThrow().equals(action);
        }

        @Override
        public void record(PolicyUseRecord record) {
            policyUses.add(record);
        }
    };
    private final FirstOwnerInviteeIdentitySetupCapability identities = new FirstOwnerInviteeIdentitySetupCapability() {
        @Override
        public boolean sendSetupIfRequired(Long tenantId, String inviteeEmail, String platformOperatorRef, String locale) {
            return false;
        }

        @Override
        public String requireIdentityEmail(Long identityId) {
            return INVITEE_EMAIL;
        }

        @Override
        public String requireActiveIdentityEmail(Long identityId) {
            return INVITEE_EMAIL;
        }
    };
    private final RecordingMessageTracing tracing = new RecordingMessageTracing();
    private final OutboxRelayPolicy policy =
        new OutboxRelayPolicy(10, 3, Duration.ofSeconds(10), Duration.ofMillis(100), Duration.ZERO);
    private final Set<String> inbox = ConcurrentHashMap.newKeySet();
    private final List<Handled> handled = new CopyOnWriteArrayList<>();
    private final List<String> handledCommands = new CopyOnWriteArrayList<>();
    private final Set<String> commandKeys = ConcurrentHashMap.newKeySet();
    private final Map<String, CommandRecord> keyHolders = new ConcurrentHashMap<>();
    private final Map<String, String> keyFingerprints = new ConcurrentHashMap<>();
    private final Map<String, CommandRecord> commandOutcomes = new ConcurrentHashMap<>();
    private final OwnerCommandIdempotency commandRecords = new CommandRecords();
    private final ThreadLocal<Transaction> transaction = new ThreadLocal<>();
    private final AtomicInteger failuresLeft = new AtomicInteger();
    private final Map<String, AtomicInteger> failuresOfEvent = new ConcurrentHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger duplicates = new AtomicInteger();
    private final PluginRuntimeDescriptor.EventSubscription subscription;
    private final BoundEventHandler bound;
    private final PluginRuntimeDescriptor.EventSubscription setupSubscription;
    private final BoundEventHandler setupBound;
    private final String reliability;
    private volatile boolean pluginsStarted = true;
    private volatile boolean failing;
    private volatile Error nextError;
    private volatile boolean hanging;
    private final AtomicInteger hangsLeft = new AtomicInteger();
    private final AtomicInteger overrunsLeft = new AtomicInteger();
    private final AtomicInteger commandCalls = new AtomicInteger();
    private final AtomicInteger commandFailuresLeft = new AtomicInteger();
    private volatile ErrorCategory commandFailure;
    private volatile Duration deadline = Duration.ofSeconds(5);
    private volatile boolean databaseDown;
    private volatile String commandAccessPolicy = CommandPolicies.ACCESS_DECLARED_CONSUMERS;

    private final UnitOfWork unitOfWork = new UnitOfWork() {
        @Override
        public <T> T required(Supplier<T> work) {
            return inTransaction(work);
        }

        @Override
        public <T> T readOnly(Supplier<T> work) {
            return inTransaction(work);
        }
    };

    private final PersistenceCapability persistence = new PersistenceCapability() {
        @Override
        public <R> R repository(Class<R> repositoryType) {
            throw new UnsupportedOperationException("the conformance handler keeps no repository");
        }

        @Override
        public UnitOfWork unitOfWork() {
            return unitOfWork;
        }
    };

    private final EventInboxStore inboxStore = (owner, handlerId, message) -> {
        Transaction current = transaction.get();
        if (current == null) {
            throw new IllegalStateException("the inbox is written in the subscriber's unit of work");
        }
        String key = owner + "|" + handlerId + "|" + message.eventId();
        if (inbox.contains(key) || current.inbox.contains(key)) {
            duplicates.incrementAndGet();
            return false;
        }
        current.inbox.add(key);
        return true;
    };

    /** A world whose event type is CRITICAL and whose subscriber records each event in its persistent inbox. */
    public ConformanceWorld() {
        this(PluginRuntimeDescriptor.EventSubscription.PERSISTENT_INBOX, "CRITICAL");
    }

    private ConformanceWorld(String idempotencyPolicy, String reliability) {
        this.subscription = new PluginRuntimeDescriptor.EventSubscription(
            "member-accepted", EVENT, ">=1.0.0 <2.0.0", HANDLER, "event-standard", idempotencyPolicy);
        this.bound = new BoundEventHandler(SUBSCRIBER, subscription, new Projection(), MemberAccepted.class);
        this.setupSubscription = new PluginRuntimeDescriptor.EventSubscription("invitee-setup-fanout", SETUP_EVENT,
            ">=1.0.0 <2.0.0", SETUP_HANDLER, "event-standard", PluginRuntimeDescriptor.EventSubscription.PERSISTENT_INBOX,
            TenantAccessPolicy.FIRST_OWNER_INVITEE_SETUP_FANOUT.id());
        this.setupBound = new BoundEventHandler(SUBSCRIBER, setupSubscription, new InviteeSetupFanout(),
            FirstOwnerInviteeSetupCompletedEvent.class);
        this.reliability = reliability;
    }

    /**
     * Returns a world whose event type is BEST_EFFORT and whose subscription keeps no inbox (root
     * BRX-RS-MANIFEST-025). The subscriber owns no persistence, so its handler runs outside any unit of work.
     *
     * @return the world
     */
    public static ConformanceWorld withoutInbox() {
        return new ConformanceWorld(PluginRuntimeDescriptor.EventSubscription.NO_DURABLE_IDEMPOTENCY, "BEST_EFFORT");
    }

    public InMemoryOutboxStore outbox() {
        return outbox;
    }

    /** The platform audit of operators' replays, discards and republishes (Kafka design D33). */
    public RecordingMessageAudit audit() {
        return audit;
    }

    /**
     * Returns the management endpoint over the publisher's outbox, recording in {@link #audit()}, with the default
     * outbox retention.
     *
     * @return the endpoint
     */
    public OutboxEndpoint outboxEndpoint() {
        return new OutboxEndpoint(() -> () -> Map.of(OUTBOX, outbox), () -> audit,
            CanonicalMessagingTables.OUTBOX_RETENTION, Clock.systemUTC());
    }

    /** The spans the relay and the delivery core start, which the transport's harness hands its relay. */
    public RecordingMessageTracing tracing() {
        return tracing;
    }

    /** Three attempts, 100 ms backoff per attempt, no jitter, a 10 s lease. */
    public OutboxRelayPolicy policy() {
        return policy;
    }

    /**
     * Returns a new delivery core over this world, as a host that starts builds one.
     *
     * @return the core
     */
    public EventDelivery newDelivery() {
        return newDelivery(deadline);
    }

    /**
     * Returns a new delivery core whose event and command handlers have the given deadline, as a host gives both half
     * its relay lease.
     *
     * @param deadline how long a handler may take
     * @return the core
     */
    public EventDelivery newDelivery(Duration deadline) {
        return new EventDelivery(subscribers(),
            owner -> SUBSCRIBER.equals(owner) && subscription.persistentInbox() ? Optional.of(persistence) : Optional.empty(),
            inboxStore, new ObjectMapper(), Clock.systemUTC(), deadline,
            new PolicyEventDeliveryScopes(() -> identities, () -> policyAudit), tracing)
            .deliveringCommandsWith(new CommandDelivery(commandProviders(),
                owner -> SUBSCRIBER.equals(owner) ? Optional.of(persistence) : Optional.empty(), inboxStore,
                commandRecords, new ObjectMapper(), Clock.systemUTC(), deadline, tracing));
    }

    /**
     * Commits a command the publisher submits to its outbox, as its unit of work does (Kafka design §14 K22).
     *
     * @param commandId command id
     * @param idempotencyKey business key, also its partition key
     * @return the outbox message
     */
    public CanonicalOutboxMessage submit(String commandId, String idempotencyKey) {
        return submit(commandId, idempotencyKey, null, null);
    }

    /**
     * Commits a command the publisher submits in a tenant, for a user or for none (standard-link plan L4.4, L4.5).
     *
     * @param commandId command id
     * @param idempotencyKey business key, also its partition key
     * @param tenantId the command's tenant, or {@code null} for a platform command
     * @param actorId the user the publisher acted for, or {@code null}
     * @return the outbox message
     */
    public CanonicalOutboxMessage submit(String commandId, String idempotencyKey, Long tenantId, String actorId) {
        return submit(commandId, idempotencyKey, tenantId, actorId, "sku-" + idempotencyKey);
    }

    /**
     * Commits a command whose content is the given stock unit, under a key that another command may have taken
     * already (standard-link plan point 8).
     *
     * @param commandId command id
     * @param idempotencyKey business key, also its partition key
     * @param tenantId the command's tenant, or {@code null} for a platform command
     * @param actorId the user the publisher acted for, or {@code null}
     * @param sku the content
     * @return the outbox message
     */
    public CanonicalOutboxMessage submit(String commandId, String idempotencyKey, Long tenantId, String actorId,
            String sku) {
        CanonicalOutboxMessage message = new CanonicalOutboxMessage(commandId, commandId, "COMMAND", COMMAND, "1.0.0",
            "STANDARD", PUBLISHER, tenantId == null ? "PLATFORM" : "TENANT", tenantId, idempotencyKey,
            OffsetDateTime.now(ZoneOffset.UTC), "correlation-" + commandId, null, TRACEPARENT, "conformance=1",
            CommandMessages.encode(new ObjectMapper(), new Reserve(sku), COMMAND, idempotencyKey, actorId), 0);
        outbox.append(message);
        return message;
    }

    /**
     * Reads a command's status as a sender's typed client does (standard-link plan point 7): the provider's records,
     * the commands the transport parked at the provider, and the publisher's outbox, which only the publisher reads.
     *
     * @param sender the plugin that asks
     * @param tenantId the tenant of the call that asks, or {@code null}
     * @param commandId the command id
     * @param broker the broker transport under test, or {@code null} for the in-process transport
     * @return the status
     */
    public CommandStatus commandStatus(String sender, Long tenantId, String commandId,
            EventSubscriptionTransport broker) {
        return new CommandStatusReader(commandProviders(), () -> commandRecords, () -> broker).status(sender, COMMAND,
            tenantId, commandId, PUBLISHER.equals(sender) ? outbox::sentCommand : id -> Optional.empty());
    }

    /**
     * Makes the provider take {@link #COMMAND} only on behalf of a user holding {@code permission} (standard-link plan
     * L4.4). Call it before the transport starts.
     *
     * @param permission the permission the publisher's user must hold
     */
    public void commandsOnlyOnBehalfOf(String permission) {
        commandAccessPolicy = CommandPolicies.ACCESS_ON_BEHALF_OF + permission;
    }

    /**
     * Returns the ids of the commands the provider's handler committed, in order.
     *
     * @return command ids
     */
    public List<String> handledCommands() {
        return List.copyOf(handledCommands);
    }

    /**
     * Whether a committed delivery of the message is recorded in the subscriber's inbox.
     *
     * @param messageId event or command id
     * @return whether it is recorded
     */
    public boolean inboxed(String messageId) {
        return inbox.stream().anyMatch(key -> key.endsWith("|" + messageId));
    }

    /**
     * Commits an event to the publisher's outbox, as the publisher's unit of work does.
     *
     * @param eventId event id
     * @param partitionKey partition key
     * @param tenantId tenant of a TENANT event, or {@code null} for a PLATFORM event
     * @param memberId payload member
     * @return the outbox message
     */
    public CanonicalOutboxMessage publish(String eventId, String partitionKey, Long tenantId, long memberId) {
        return publish(eventId, partitionKey, tenantId, memberId, "1.0.0");
    }

    /**
     * Commits an event of a schema version to the publisher's outbox.
     *
     * @param eventId event id
     * @param partitionKey partition key
     * @param tenantId tenant of a TENANT event, or {@code null} for a PLATFORM event
     * @param memberId payload member
     * @param schemaVersion schema version
     * @return the outbox message
     */
    public CanonicalOutboxMessage publish(String eventId, String partitionKey, Long tenantId, long memberId,
            String schemaVersion) {
        CanonicalOutboxMessage message = message(eventId, partitionKey, tenantId, memberId, schemaVersion);
        outbox.append(message);
        return message;
    }

    /**
     * Returns the event the publisher would commit, without committing it, for a test that sends it itself.
     *
     * @param eventId event id
     * @param partitionKey partition key
     * @param tenantId tenant of a TENANT event, or {@code null} for a PLATFORM event
     * @param memberId payload member
     * @return the event
     */
    public CanonicalOutboxMessage message(String eventId, String partitionKey, Long tenantId, long memberId) {
        return message(eventId, partitionKey, tenantId, memberId, "1.0.0");
    }

    private CanonicalOutboxMessage message(String eventId, String partitionKey, Long tenantId, long memberId,
            String schemaVersion) {
        return new CanonicalOutboxMessage("message-" + UUID.randomUUID(), eventId, "EVENT", EVENT, schemaVersion,
            reliability, PUBLISHER, tenantId == null ? "PLATFORM" : "TENANT", tenantId, partitionKey,
            OffsetDateTime.now(ZoneOffset.UTC), "correlation-" + eventId, null, TRACEPARENT, "conformance=1",
            "{\"tenantId\":" + tenantId + ",\"memberId\":" + memberId + "}", 0);
    }

    /**
     * Commits {@link #SETUP_EVENT} to the identity publisher's outbox: a PLATFORM fact that an identity finished its
     * setup, which the subscriber fans out under its cross-tenant policy.
     *
     * @param eventId event id
     * @param identityId the identity
     * @return the outbox message
     */
    public CanonicalOutboxMessage publishSetupCompleted(String eventId, long identityId) {
        CanonicalOutboxMessage message = new CanonicalOutboxMessage("message-" + UUID.randomUUID(), eventId, "EVENT",
            SETUP_EVENT, "1.0.0", "CRITICAL", IDENTITY_PUBLISHER, "PLATFORM", null, "IDENTITY:" + identityId,
            OffsetDateTime.now(ZoneOffset.UTC), "correlation-" + eventId, null, TRACEPARENT, "conformance=1",
            "{\"identityId\":" + identityId + "}", 0);
        outbox.append(message);
        return message;
    }

    /** The uses of the cross-tenant policy the delivery core recorded, in order. */
    public List<PolicyUseRecord> policyUses() {
        return List.copyOf(policyUses);
    }

    /** Makes the plugins started or not; until they start no event can be judged or delivered. */
    public void pluginsStarted(boolean started) {
        pluginsStarted = started;
    }

    /** Makes the handler fail on its next calls. */
    public void failNext(int count) {
        failuresLeft.set(count);
    }

    /** Makes the handler fail on the next deliveries of one event. */
    public void failNextOf(String eventId, int count) {
        failuresOfEvent.put(eventId, new AtomicInteger(count));
    }

    /** Makes the handler throw this error once, as a handler whose class cannot load does. */
    public void failNextWith(Error error) {
        this.nextError = error;
    }

    /** Makes the handler return a stage that never completes, as a handler that hangs does. */
    public void hanging(boolean hanging) {
        this.hanging = hanging;
    }

    /** Makes the handler hang on its next calls, as a handler past its deadline does. */
    public void hangNext(int count) {
        hangsLeft.set(count);
    }

    /**
     * Gives the handlers of the delivery cores built from now on this deadline instead of five seconds, as a host
     * with a shorter relay lease does.
     *
     * @param deadline how long a handler may take
     */
    public void handlerDeadline(Duration deadline) {
        this.deadline = deadline;
    }

    /**
     * Makes the event and command handlers do their work and return only after their deadline on their next calls,
     * as a synchronous handler that runs past it does (standard-link plan point 3).
     */
    public void overrunNext(int count) {
        overrunsLeft.set(count);
    }

    /**
     * Makes the command handler fail on its next calls, after it has done its work: with a stable failure of the
     * category, or with {@code null}, with an exception of no code (standard-link plan point 9).
     *
     * @param category the failure's category, or {@code null}
     * @param count how many calls fail
     */
    public void failNextCommands(ErrorCategory category, int count) {
        commandFailure = category;
        commandFailuresLeft.set(count);
    }

    /** How often the command handler was called, including the attempts whose work rolled back. */
    public int commandCalls() {
        return commandCalls.get();
    }

    /** Makes the subscriber's database unreachable, or reachable again: its units of work cannot begin. */
    public void subscriberDatabaseDown(boolean down) {
        this.databaseDown = down;
    }

    /** Makes the handler fail until told otherwise. */
    public void failing(boolean failing) {
        this.failing = failing;
    }

    /** The events the handler committed, in commit order. */
    public List<Handled> handled() {
        return List.copyOf(handled);
    }

    /** The ids of the events the handler committed, in commit order. */
    public List<String> handledIds() {
        return handled.stream().map(Handled::eventId).toList();
    }

    /** How often the handler was called, committed or not. */
    public int calls() {
        return calls.get();
    }

    /** How often the inbox skipped an event the handler had committed. */
    public int duplicates() {
        return duplicates.get();
    }

    private EventSubscribers subscribers() {
        return new EventSubscribers() {
            @Override
            public boolean ready() {
                return pluginsStarted;
            }

            @Override
            public Optional<String> publisherOf(String eventType) {
                return EVENT.equals(eventType) ? Optional.of(PUBLISHER)
                    : SETUP_EVENT.equals(eventType) ? Optional.of(IDENTITY_PUBLISHER) : Optional.empty();
            }

            @Override
            public List<DeclaredSubscriber> subscribersOf(String eventType) {
                return subscriptions().stream()
                    .filter(declared -> declared.subscription().eventType().equals(eventType))
                    .toList();
            }

            @Override
            public List<DeclaredSubscriber> subscriptions() {
                return List.of(new DeclaredSubscriber(SUBSCRIBER, subscription, bound),
                    new DeclaredSubscriber(SUBSCRIBER, setupSubscription, setupBound));
            }

            @Override
            public Set<String> publishedEventTypes() {
                return new java.util.TreeSet<>(List.of(EVENT, SETUP_EVENT));
            }
        };
    }

    private CommandProviders commandProviders() {
        return new CommandProviders() {
            @Override
            public boolean ready() {
                return pluginsStarted;
            }

            @Override
            public Optional<PluginRuntimeManager.CommandProvider> providerOf(String commandType) {
                return COMMAND.equals(commandType) ? Optional.of(provider()) : Optional.empty();
            }

            @Override
            public List<PluginRuntimeManager.CommandProvider> providers() {
                return List.of(provider());
            }

            @Override
            public Optional<PluginRuntimeDescriptor.ConsumedCommand> consumed(String pluginId, String commandType) {
                return PUBLISHER.equals(pluginId) && COMMAND.equals(commandType)
                    ? Optional.of(new PluginRuntimeDescriptor.ConsumedCommand(COMMAND, ">=1.0.0 <2.0.0", true))
                    : Optional.empty();
            }

            private PluginRuntimeManager.CommandProvider provider() {
                return new PluginRuntimeManager.CommandProvider(SUBSCRIBER,
                    new PluginRuntimeDescriptor.ProvidedCommand(COMMAND, COMMAND_HANDLER, "1.0.0", "event-standard",
                        "event-standard", "partition-serial", CommandPolicies.SCOPE_ENVELOPE, commandAccessPolicy,
                        "persistent-inbox"), new Stock(), Reserve.class);
            }
        };
    }

    private final class Stock implements CommandHandler<Reserve> {

        @Override
        public CompletionStage<Void> handle(CommandInvocation<Reserve> invocation) {
            commandCalls.incrementAndGet();
            Transaction current = transaction.get();
            if (current == null) {
                throw new IllegalStateException("a command runs in its provider's unit of work");
            }
            current.commands.add(invocation.command().commandId());
            overrunIfAsked();
            if (commandFailuresLeft.getAndUpdate(left -> Math.max(0, left - 1)) > 0) {
                ErrorCategory category = commandFailure;
                throw category == null ? new IllegalStateException("the stock service is failing")
                    : new StockException(category);
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    /** A stable failure of the command handler: a refusal of a business category, or one of any other. */
    private static final class StockException extends BrixException {

        private static final long serialVersionUID = 1L;

        StockException(ErrorCategory category) {
            super(new ErrorCode() {
                @Override
                public String wireCode() {
                    return STOCK_REFUSED;
                }

                @Override
                public ErrorCategory category() {
                    return category;
                }
            }, Map.of(), null);
        }
    }

    private <T> T inTransaction(Supplier<T> work) {
        if (transaction.get() != null) {
            return work.get();
        }
        if (databaseDown) {
            throw new PersistenceCapabilityException(PersistenceCapabilityException.Code.UNAVAILABLE,
                Map.of("owner", SUBSCRIBER));
        }
        Transaction current = new Transaction();
        transaction.set(current);
        try {
            T result = work.get();
            commit(current);
            return result;
        } finally {
            transaction.remove();
        }
    }

    private void commit(Transaction current) {
        List<String> added = new ArrayList<>();
        for (String key : current.inbox) {
            if (!inbox.add(key)) {
                inbox.removeAll(added);
                throw new IllegalStateException("duplicate inbox key " + key);
            }
            added.add(key);
        }
        handled.addAll(current.handled);
        commandKeys.addAll(current.commandKeys);
        keyHolders.putAll(current.held);
        keyFingerprints.putAll(current.fingerprints);
        current.outcomes.forEach(commandOutcomes::putIfAbsent);
        handledCommands.addAll(current.commands);
    }

    /**
     * The provider's command records, written in its unit of work with the inbox and committed with it: the key each
     * handled command holds, and the outcome of each command that took none (root BRX-RS-MESSAGE-014; standard-link
     * plan point 7).
     */
    private final class CommandRecords implements OwnerCommandIdempotency {

        @Override
        public boolean reserve(String owner, String handlerId, CanonicalOutboxMessage message, String scope,
                String key, String fingerprint) {
            Transaction current = inUnitOfWork("the idempotency record");
            String taken = owner + "|" + handlerId + "|" + scope + "|" + key;
            if (commandKeys.contains(taken) || current.commandKeys.contains(taken)) {
                return false;
            }
            current.commandKeys.add(taken);
            current.held.put(taken, new CommandRecord(message.messageId(), CommandStatus.State.SUCCEEDED, scope, key,
                message.tenantId(), null, null, OffsetDateTime.now(ZoneOffset.UTC)));
            current.fingerprints.put(taken, fingerprint);
            return true;
        }

        @Override
        public Optional<Holder> holder(String owner, String handlerId, String scope, String key) {
            String taken = owner + "|" + handlerId + "|" + scope + "|" + key;
            Transaction current = transaction.get();
            boolean own = current != null && current.held.containsKey(taken);
            CommandRecord holding = own ? current.held.get(taken) : keyHolders.get(taken);
            String fingerprint = own ? current.fingerprints.get(taken) : keyFingerprints.get(taken);
            return Optional.ofNullable(holding).map(record -> new Holder(record.commandId(), fingerprint));
        }

        @Override
        public void recordOutcome(String owner, String handlerId, CommandRecord outcome) {
            inUnitOfWork("a command outcome").outcomes.putIfAbsent(owner + "|" + handlerId + "|" + outcome.commandId(),
                outcome);
        }

        @Override
        public Optional<CommandRecord> find(String owner, String handlerId, String commandId) {
            String prefix = owner + "|" + handlerId + "|";
            return keyHolders.entrySet().stream()
                .filter(held -> held.getKey().startsWith(prefix) && held.getValue().commandId().equals(commandId))
                .map(Map.Entry::getValue)
                .findFirst()
                .or(() -> Optional.ofNullable(commandOutcomes.get(prefix + commandId)));
        }

        private Transaction inUnitOfWork(String what) {
            Transaction current = transaction.get();
            if (current == null) {
                throw new IllegalStateException(what + " is written in the provider's unit of work");
            }
            return current;
        }
    }

    private final class Projection implements EventHandler<MemberAccepted> {

        @Override
        public CompletionStage<Void> handle(EventInvocation<MemberAccepted> invocation) {
            calls.incrementAndGet();
            Error error = nextError;
            if (error != null) {
                nextError = null;
                throw error;
            }
            if (hanging || hangsLeft.getAndUpdate(left -> Math.max(0, left - 1)) > 0) {
                return new CompletableFuture<>();
            }
            AtomicInteger ofEvent = failuresOfEvent.get(invocation.event().eventId());
            boolean eventFails = ofEvent != null && ofEvent.getAndUpdate(left -> Math.max(0, left - 1)) > 0;
            if (failing || eventFails || failuresLeft.getAndUpdate(left -> Math.max(0, left - 1)) > 0) {
                throw new IllegalStateException("the projection is failing");
            }
            Handled event = new Handled(invocation.event().eventId(), invocation.event().payload().memberId(),
                TenantScopes.requested(), invocation.trace().traceparent(), invocation.deliveryAttempt());
            Transaction current = transaction.get();
            if (current == null) {
                // Without a unit of work there is nothing to commit with.
                handled.add(event);
            } else {
                current.handled.add(event);
            }
            overrunIfAsked();
            return CompletableFuture.completedFuture(null);
        }
    }

    /** Keeps a handler that has done its work from returning until its deadline has passed, when asked to. */
    private void overrunIfAsked() {
        if (overrunsLeft.getAndUpdate(left -> Math.max(0, left - 1)) > 0) {
            try {
                Thread.sleep(deadline.toMillis() + 200);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Fans an identity's completed setup out to the tenants the policy reaches. */
    private final class InviteeSetupFanout implements EventHandler<FirstOwnerInviteeSetupCompletedEvent> {

        @Override
        public CompletionStage<Void> handle(EventInvocation<FirstOwnerInviteeSetupCompletedEvent> invocation) {
            // The persistence adapter reports each tenant a policy scope reaches; in this world the handler does.
            FANOUT_TENANTS.forEach(TenantScopes::reportBound);
            Handled event = new Handled(invocation.event().eventId(), invocation.event().payload().identityId(),
                TenantScopes.requested(), invocation.trace().traceparent(), invocation.deliveryAttempt());
            Transaction current = transaction.get();
            if (current == null) {
                handled.add(event);
            } else {
                current.handled.add(event);
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class Transaction {
        private final List<String> inbox = new ArrayList<>();
        private final List<Handled> handled = new ArrayList<>();
        private final List<String> commandKeys = new ArrayList<>();
        private final Map<String, CommandRecord> held = new java.util.LinkedHashMap<>();
        private final Map<String, String> fingerprints = new java.util.LinkedHashMap<>();
        private final Map<String, CommandRecord> outcomes = new java.util.LinkedHashMap<>();
        private final List<String> commands = new ArrayList<>();
    }
}
