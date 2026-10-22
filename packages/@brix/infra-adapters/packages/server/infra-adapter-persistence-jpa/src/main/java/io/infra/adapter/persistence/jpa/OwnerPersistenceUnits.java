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

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.sql.DataSource;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.ManagedType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.jpa.EntityManagerFactoryInfo;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;

import io.infra.adapter.persistence.jpa.OwnerDirectory.OwnerDeclaration;
import io.runtime.manifest.loader.PluginManifestLoader;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.capability.PersistenceCapabilityException;

/**
 * Creates and caches the persistence capability of every data owner that declares one.
 *
 * <p>All owners share one JPA persistence unit and one transaction manager; isolation comes from the
 * owner unit of work, the repository guard and the startup validation instead of separate
 * connection pools, which keeps a single pool and a single migration runner (root blueprint
 * BRX-RS-DATA-027 allows any mechanism that gives the same proof).</p>
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
    private final ConcurrentMap<String, JpaOwnerPersistence> units = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, OwnerPersistenceValidator> validators = new ConcurrentHashMap<>();

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

    @Override
    public void close() {
        validators.values().forEach(OwnerPersistenceValidator::close);
        validators.clear();
        units.clear();
    }

    private JpaOwnerPersistence create(OwnerDeclaration owner) {
        Set<Class<?>> ownerTypes = managedTypesOf(owner.ownerId());
        OwnerPersistenceValidator validator = OwnerPersistenceValidator.create(
            owner.ownerId(), ownerTypes, entityManagerFactory, dataSource, validateSchema);
        validators.put(owner.ownerId(), validator);
        OwnerUnitOfWork unitOfWork = new OwnerUnitOfWork(
            owner.ownerId(),
            entityManagerFactory,
            sharedEntityManager,
            transactionManager,
            transactionTimeout,
            failureTranslator);
        log.info("Owner persistence unit '{}' ready with {} managed types (storageId={})",
            owner.ownerId(), ownerTypes.size(), owner.storageId());
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

    private static PersistenceCapabilityException rejected(Map<String, String> safeParameters, String detail) {
        return new PersistenceCapabilityException(
            PersistenceCapabilityException.Code.REPOSITORY_REJECTED,
            safeParameters,
            new IllegalStateException("Owner persistence rejected: " + detail));
    }
}
