/*
 * Copyright 2026 Brix Authors
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
package io.infra.adapter.persistence.jpa;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.sql.DataSource;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ManagedType;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.jpa.EntityManagerFactoryInfo;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import tools.jackson.databind.ObjectMapper;

import io.infra.adapter.persistence.jpa.OwnerDirectory.OwnerDeclaration;
import io.runtime.manifest.loader.PluginManifestLoader;
import io.runtime.orchestrator.command.OwnerCommandIdempotency;
import io.runtime.orchestrator.endpoint.OwnerEndpointIdempotency;
import io.runtime.orchestrator.event.BestEffortEventSender;
import io.runtime.orchestrator.event.EventInboxStore;
import io.runtime.orchestrator.outbox.CanonicalMessagingTables;
import io.runtime.orchestrator.outbox.MessageOperationAudit;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.persistence.PolicyAuditWriter;
import io.runtime.orchestrator.persistence.TenantIsolationRegister;
import io.runtime.orchestrator.task.TaskRunClaims;
import io.runtime.sdk.capability.CommandCapability;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.IdGeneratorCapability;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.capability.PersistenceCapabilityException;

/**
 * Creates and caches the persistence capability of every data owner that declares one.
 *
 * <p>All owners share one JPA persistence unit and one transaction manager; isolation comes from the
 * owner unit of work, the repository guard and the startup validation instead of separate
 * connection pools, which keeps a single pool and a single migration runner (root blueprint
 * BRX-RS-DATA-027 allows any mechanism that gives the same proof).</p>
 *
 * <p>A table belongs to one owner: an owner's unit is refused when another owner's entity maps one of
 * its tables (root BRX-RS-DATA-001, DATA-027). A registered custodian writes columns of another owner's
 * table without mapping it.</p>
 */
public final class OwnerPersistenceUnits implements OwnerPersistenceProvider, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OwnerPersistenceUnits.class);

    private final EntityManagerFactory entityManagerFactory;
    private final JpaTransactionManager transactionManager;
    private final DataSource dataSource;
    private final Duration transactionTimeout;
    private final boolean validateSchema;
    private final OwnerDirectory owners;
    private final EntityManager sharedEntityManager;
    private final PersistenceFailureTranslator failureTranslator;
    private final Set<String> globalTables;
    private final ObjectMapper json;
    private final ConcurrentMap<String, JpaOwnerPersistence> units = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Optional<EventBusCapability>> eventBuses = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Optional<CommandCapability>> commandBuses = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, OwnerPersistenceValidator> validators = new ConcurrentHashMap<>();
    private volatile Supplier<BestEffortEventSender> bestEffort = () -> null;

    /**
     * Creates the owner persistence registry.
     *
     * @param entityManagerFactory shared persistence unit
     * @param transactionManager JPA transaction manager of {@code entityManagerFactory}
     * @param dataSource data source of {@code entityManagerFactory}
     * @param transactionTimeout deadline of every owner transaction
     * @param validateSchema whether owner tables are checked against the database at startup
     * @param classLoader class loader that sees every plugin manifest
     */
    public OwnerPersistenceUnits(
            EntityManagerFactory entityManagerFactory,
            JpaTransactionManager transactionManager,
            DataSource dataSource,
            Duration transactionTimeout,
            boolean validateSchema,
            ClassLoader classLoader) {
        this(entityManagerFactory, transactionManager, dataSource, transactionTimeout, validateSchema, classLoader,
            new ObjectMapper());
    }

    /**
     * Creates the owner persistence registry whose owner outboxes record payloads with the Host's mapper.
     *
     * @param entityManagerFactory shared persistence unit
     * @param transactionManager JPA transaction manager of {@code entityManagerFactory}
     * @param dataSource data source of {@code entityManagerFactory}
     * @param transactionTimeout deadline of every owner transaction
     * @param validateSchema whether owner tables are checked against the database at startup
     * @param classLoader class loader that sees every plugin manifest
     * @param json writes the payloads of outbox events
     */
    public OwnerPersistenceUnits(
            EntityManagerFactory entityManagerFactory,
            JpaTransactionManager transactionManager,
            DataSource dataSource,
            Duration transactionTimeout,
            boolean validateSchema,
            ClassLoader classLoader,
            ObjectMapper json) {
        this(entityManagerFactory, transactionManager, dataSource, transactionTimeout, validateSchema, classLoader,
            TenantIsolationRegister.GLOBAL_TABLES, json);
    }

    /**
     * Creates the owner persistence registry with an explicit list of global tables; production
     * uses the list of multi-tenant §6.1, tests add the tables of their fixtures.
     */
    OwnerPersistenceUnits(
            EntityManagerFactory entityManagerFactory,
            JpaTransactionManager transactionManager,
            DataSource dataSource,
            Duration transactionTimeout,
            boolean validateSchema,
            ClassLoader classLoader,
            Set<String> globalTables) {
        this(entityManagerFactory, transactionManager, dataSource, transactionTimeout, validateSchema, classLoader,
            globalTables, new ObjectMapper());
    }

    OwnerPersistenceUnits(
            EntityManagerFactory entityManagerFactory,
            JpaTransactionManager transactionManager,
            DataSource dataSource,
            Duration transactionTimeout,
            boolean validateSchema,
            ClassLoader classLoader,
            Set<String> globalTables,
            ObjectMapper json) {
        this.json = Objects.requireNonNull(json, "json must not be null");
        this.globalTables = Set.copyOf(Objects.requireNonNull(globalTables, "globalTables must not be null"));
        this.entityManagerFactory = Objects.requireNonNull(entityManagerFactory, "entityManagerFactory must not be null");
        this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager must not be null");
        if (transactionManager.getEntityManagerFactory() != entityManagerFactory) {
            throw new IllegalStateException(
                "The JPA transaction manager must manage the shared persistence unit used for owner persistence");
        }
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.transactionTimeout = Objects.requireNonNull(transactionTimeout, "transactionTimeout must not be null");
        this.validateSchema = validateSchema;
        this.owners = new OwnerDirectory(classLoader, new PluginManifestLoader());
        this.sharedEntityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
        this.failureTranslator = new PersistenceFailureTranslator(
            entityManagerFactory instanceof EntityManagerFactoryInfo info && info.getJpaDialect() != null
                ? info.getJpaDialect()
                : new HibernateJpaDialect());
    }

    /**
     * Sets how the Owners' BEST_EFFORT events are sent (Kafka design §14 K21).
     *
     * @param sender supplies the sender, or {@code null} when there is none and such events are dropped
     * @return this registry
     */
    public OwnerPersistenceUnits sendingBestEffortEventsWith(Supplier<BestEffortEventSender> sender) {
        Objects.requireNonNull(sender, "sender must not be null");
        this.bestEffort = sender;
        return this;
    }

    /**
     * Makes the owners' persistent event inboxes (root BRX-RS-MESSAGE-025), the {@code data.inbox} tables of
     * their manifests, written on each owner's current transaction.
     *
     * @return the event inbox store
     */
    public EventInboxStore eventInboxStore() {
        return new JdbcEventInboxStore(owners, dataSource, java.time.Clock.systemUTC());
    }

    /**
     * Makes the runtime policy audit writer over the custodial registration the active manifests
     * declare (root BRX-RS-DATA-045, DATA-047). Without a registration it covers no action.
     *
     * @param ids supplies the Host's id generator, which a registration needs; may supply {@code null}
     * @return the policy audit writer
     */
    public PolicyAuditWriter policyAuditWriter(Supplier<IdGeneratorCapability> ids) {
        return new JdbcPolicyAuditWriter(owners, entityManagerFactory, transactionManager, dataSource, transactionTimeout, ids);
    }

    /**
     * Makes the runtime custodian that records operators' actions on messages in the platform audit (root
     * BRX-RS-DATA-045, MESSAGE-028; Kafka design D33). It needs the platform audit owner's registration when it
     * first records; without one every action is refused.
     *
     * @param ids supplies the Host's id generator; may supply {@code null}
     * @return the audit of message operations
     */
    public MessageOperationAudit messageOperationAudit(Supplier<IdGeneratorCapability> ids) {
        return new JdbcMessageOperationAudit(owners, entityManagerFactory, transactionManager, dataSource,
            transactionTimeout, ids);
    }

    @Override
    public <T> T readOnly(String ownerModuleId, Duration timeout, Supplier<T> work) {
        Optional<PersistenceCapability> owner = forOwner(ownerModuleId);
        if (owner.isEmpty()) {
            return work.get();
        }
        return ((OwnerUnitOfWork) owner.get().unitOfWork()).readOnlyWithin(timeout, work);
    }

    @Override
    public <T> T readWrite(String ownerModuleId, Duration timeout, Supplier<T> work) {
        Optional<PersistenceCapability> owner = forOwner(ownerModuleId);
        if (owner.isEmpty()) {
            return work.get();
        }
        return ((OwnerUnitOfWork) owner.get().unitOfWork()).requiredWithin(timeout, work);
    }

    @Override
    public Optional<PersistenceCapability> forOwner(String ownerModuleId) {
        Objects.requireNonNull(ownerModuleId, "ownerModuleId must not be null");
        return owners.owner(ownerModuleId)
            .filter(OwnerDeclaration::declaresPersistence)
            .map(owner -> units.computeIfAbsent(owner.ownerId(), ownerId -> create(owner)));
    }

    /**
     * Returns the persistence capability of the owner that packages {@code component}.
     *
     * <p>Used while platform modules are still assembled by the framework container: the owner is
     * derived from the component's artifact, never passed in by the module.</p>
     *
     * @param component a class packaged in the owner's artifact
     * @return the owner's persistence capability
     */
    public PersistenceCapability forComponent(Class<?> component) {
        Objects.requireNonNull(component, "component must not be null");
        OwnerDeclaration owner = owners.ownerOf(component).orElseThrow(() -> rejected(
            Map.of(),
            component.getName() + " is not packaged with a plugin manifest"));
        if (!owner.declaresPersistence()) {
            throw rejected(Map.of("owner", owner.ownerId()), "the manifest of '" + owner.ownerId()
                + "' does not declare " + PersistenceCapability.class.getName());
        }
        return forOwner(owner.ownerId()).orElseThrow();
    }

    /**
     * Returns the outbox event bus of an owner whose manifest declares {@code data.outbox} and
     * {@link EventBusCapability} (P4 design D13).
     */
    @Override
    public Optional<EventBusCapability> eventBusFor(String ownerModuleId) {
        Objects.requireNonNull(ownerModuleId, "ownerModuleId must not be null");
        // Every Owner that declares the event bus gets its own (Kafka design §14 K21): it writes CRITICAL and
        // STANDARD events to its data.outbox, and sends BEST_EFFORT ones with the identity the runtime binds.
        return eventBuses.computeIfAbsent(ownerModuleId, ownerId -> owners.declaresEventBus(ownerId)
            ? Optional.of((EventBusCapability) new OwnerOutboxEventBus(ownerId, owners.outboxOf(ownerId).orElse(null),
                owners.publicationsOf(ownerId), dataSource, json, Clock.systemUTC(), () -> bestEffort.get()))
            : Optional.empty());
    }

    /**
     * Returns the outbox event bus of the owner that packages {@code component}.
     *
     * <p>Used while platform modules are still assembled by the framework container: the owner is
     * derived from the component's artifact, never passed in by the module.</p>
     *
     * @param component a class packaged in the owner's artifact
     * @return the owner's outbox event bus
     */
    public EventBusCapability eventBusForComponent(Class<?> component) {
        Objects.requireNonNull(component, "component must not be null");
        OwnerDeclaration owner = owners.ownerOf(component).orElseThrow(() -> rejected(
            Map.of(),
            component.getName() + " is not packaged with a plugin manifest"));
        return eventBusFor(owner.ownerId()).orElseThrow(() -> rejected(Map.of("owner", owner.ownerId()),
            "the manifest of '" + owner.ownerId() + "' does not declare data.outbox and "
                + EventBusCapability.class.getName()));
    }

    @Override
    public void close() {
        validators.values().forEach(OwnerPersistenceValidator::close);
        validators.clear();
        units.clear();
    }

    private JpaOwnerPersistence create(OwnerDeclaration owner) {
        Set<Class<?>> ownerTypes = managedTypesOf(owner.ownerId());
        requireOneOwnerPerTable(owner.ownerId(), entityTables(),
            type -> owners.ownerOf(type).map(OwnerDeclaration::ownerId));
        OwnerPersistenceValidator validator = OwnerPersistenceValidator.create(
            owner.ownerId(), ownerTypes, entityManagerFactory, dataSource, validateSchema,
            globalTables, owner.custodianTables());
        validators.put(owner.ownerId(), validator);
        OwnerUnitOfWork unitOfWork = new OwnerUnitOfWork(
            owner.ownerId(),
            entityManagerFactory,
            sharedEntityManager,
            transactionManager,
            transactionTimeout,
            failureTranslator,
            validator.tenantTables());
        log.info("Owner persistence unit '{}' ready with {} managed types and tenant tables {} (storageId={})",
            owner.ownerId(), ownerTypes.size(), validator.tenantTables().tenantTables(), owner.storageId());
        return new JpaOwnerPersistence(
            owner.ownerId(),
            type -> owners.ownerOf(type).map(OwnerDeclaration::ownerId).orElse(null),
            sharedEntityManager,
            validator,
            unitOfWork,
            failureTranslator);
    }

    private Set<Class<?>> managedTypesOf(String ownerId) {
        Set<Class<?>> types = new LinkedHashSet<>();
        for (ManagedType<?> managedType : entityManagerFactory.getMetamodel().getManagedTypes()) {
            Class<?> javaType = managedType.getJavaType();
            if (javaType != null && owners.ownerOf(javaType)
                    .map(OwnerDeclaration::ownerId)
                    .filter(ownerId::equals)
                    .isPresent()) {
                types.add(javaType);
            }
        }
        return types;
    }

    /** Every entity of the shared persistence unit and the table it maps. */
    private Map<Class<?>, String> entityTables() {
        SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        Map<Class<?>, String> tables = new LinkedHashMap<>();
        for (EntityType<?> entity : sessionFactory.getJpaMetamodel().getEntities()) {
            Class<?> type = entity.getJavaType();
            if (type != null) {
                tables.put(type, OwnerTenantTables.tableOf(sessionFactory.getMappingMetamodel().getEntityDescriptor(type)));
            }
        }
        return tables;
    }

    /**
     * Refuses an owner whose tables another owner's entities also map (root BRX-RS-DATA-001, DATA-027).
     *
     * @param ownerId the owner whose unit is being created
     * @param entityTables every entity and the table it maps
     * @param ownerOf the owner of an entity, empty when no plugin manifest packages it
     * @throws PersistenceCapabilityException when another owner maps one of the owner's tables
     */
    static void requireOneOwnerPerTable(
            String ownerId,
            Map<Class<?>, String> entityTables,
            Function<Class<?>, Optional<String>> ownerOf) {
        Map<String, Class<?>> ownTables = new LinkedHashMap<>();
        entityTables.forEach((type, table) -> {
            if (ownerOf.apply(type).filter(ownerId::equals).isPresent()) {
                ownTables.putIfAbsent(table, type);
            }
        });
        entityTables.forEach((type, table) -> {
            Optional<String> other = ownerOf.apply(type).filter(owner -> !owner.equals(ownerId));
            Class<?> own = ownTables.get(table);
            if (other.isPresent() && own != null) {
                throw rejected(Map.of("owner", ownerId), table + " is mapped by " + own.getName() + " of '" + ownerId
                    + "' and by " + type.getName() + " of '" + other.get() + "'; a table belongs to one owner");
            }
        });
    }

    @Override
    public void requireMessagingTables(String ownerModuleId) {
        Objects.requireNonNull(ownerModuleId, "ownerModuleId must not be null");
        owners.outboxOf(ownerModuleId)
            .ifPresent(outbox -> requireCanonical(ownerModuleId, CanonicalMessagingTables.Kind.OUTBOX, outbox));
        owners.inboxOf(ownerModuleId)
            .ifPresent(inbox -> requireCanonical(ownerModuleId, CanonicalMessagingTables.Kind.INBOX, inbox));
        if (owners.providesCommands(ownerModuleId)) {
            owners.storageIdOf(ownerModuleId).ifPresent(storageId -> {
                requireCanonical(ownerModuleId, CanonicalMessagingTables.Kind.COMMAND_IDEMPOTENCY,
                    CanonicalMessagingTables.commandIdempotencyOf(storageId));
                requireCanonical(ownerModuleId, CanonicalMessagingTables.Kind.COMMAND_OUTCOME,
                    CanonicalMessagingTables.commandOutcomeOf(storageId));
            });
        }
        if (owners.providesTasks(ownerModuleId)) {
            owners.storageIdOf(ownerModuleId).ifPresent(storageId -> requireCanonical(ownerModuleId,
                CanonicalMessagingTables.Kind.TASK_RUN, CanonicalMessagingTables.taskRunOf(storageId)));
        }
        if (owners.replaysEndpointResults(ownerModuleId)) {
            owners.storageIdOf(ownerModuleId).ifPresent(storageId -> requireCanonical(ownerModuleId,
                CanonicalMessagingTables.Kind.ENDPOINT_IDEMPOTENCY,
                CanonicalMessagingTables.endpointIdempotencyOf(storageId)));
        }
    }

    @Override
    public Optional<CommandCapability> commandBusFor(String ownerModuleId) {
        Objects.requireNonNull(ownerModuleId, "ownerModuleId must not be null");
        return commandBuses.computeIfAbsent(ownerModuleId, ownerId -> owners.declaresCommandBus(ownerId)
            ? Optional.of((CommandCapability) new OwnerCommandBus(ownerId, owners.outboxOf(ownerId).orElse(null),
                owners.consumedCommandsOf(ownerId), dataSource, json, Clock.systemUTC()))
            : Optional.empty());
    }

    /**
     * Returns the providers' business idempotency records, which the runtime's command delivery writes in each
     * provider's transaction (root BRX-RS-MESSAGE-014).
     *
     * @return the idempotency store
     */
    public OwnerCommandIdempotency commandIdempotency() {
        return new JdbcOwnerCommandIdempotency(owners, dataSource, Clock.systemUTC());
    }

    /**
     * Returns the answers of the owners' endpoints whose results the runtime replays (standard-link plan point 10a).
     *
     * @return the owners' endpoint idempotency tables
     */
    public OwnerEndpointIdempotency endpointIdempotency() {
        return new JdbcOwnerEndpointIdempotency(owners, dataSource, Clock.systemUTC());
    }

    /**
     * Returns the owners' task-run tables, in which the runtime's scheduler claims each trigger of a task that runs on
     * one host (standard-link plan L6.4).
     *
     * @return the claims
     */
    public TaskRunClaims taskRunClaims() {
        return JdbcTaskRunClaims.of(owners, dataSource, Clock.systemUTC());
    }

    /**
     * Returns the work of the active owners that holds a pooled connection while it runs (query design §5.8).
     *
     * @return the relays and consumers
     */
    public ConnectionPoolBudget.Demand connectionDemand() {
        return owners.connectionDemand();
    }

    /**
     * Refuses an Owner whose canonical messaging table does not match the platform schema (root
     * BRX-RS-MESSAGE-026, Kafka design §14 K19).
     */
    private void requireCanonical(String ownerId, CanonicalMessagingTables.Kind kind, String table) {
        if (!validateSchema) {
            return;
        }
        try {
            MessagingTableCheck.require(dataSource, ownerId, kind, OwnerTenantTables.normalize(table));
        } catch (IllegalStateException mismatch) {
            throw rejected(Map.of("owner", ownerId), mismatch.getMessage());
        }
    }

    private static PersistenceCapabilityException rejected(Map<String, String> safeParameters, String detail) {
        return new PersistenceCapabilityException(
            PersistenceCapabilityException.Code.REPOSITORY_REJECTED,
            safeParameters,
            new IllegalStateException("Owner persistence rejected: " + detail));
    }
}
