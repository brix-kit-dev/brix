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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import jakarta.persistence.EntityManager;

import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.capability.PersistenceCapabilityException;
import io.runtime.sdk.capability.UnitOfWork;

/**
 * {@link PersistenceCapability} of one data owner.
 *
 * <p>Repositories are created here, not by a framework container (root blueprint BRX-RS-DATA-026).
 * Each interface is validated once, must be packaged with the owner, and is bound to the shared
 * transactional entity manager behind a guard that enforces the owner's unit of work.</p>
 */
final class JpaOwnerPersistence implements PersistenceCapability {

    private final String ownerId;
    private final Function<Class<?>, String> ownerOfType;
    private final OwnerPersistenceValidator validator;
    private final OwnerUnitOfWork unitOfWork;
    private final JpaRepositoryFactory repositoryFactory;
    private final ConcurrentMap<Class<?>, Object> repositories = new ConcurrentHashMap<>();

    /**
     * Creates the owner's persistence capability.
     *
     * @param ownerId data owner id
     * @param ownerOfType resolves the owner id a type is packaged with, or {@code null} for none
     * @param sharedEntityManager transactional entity manager of the shared persistence unit
     * @param validator owner validation built at startup
     * @param unitOfWork the owner's unit of work
     * @param failureTranslator translates repository failures
     */
    JpaOwnerPersistence(
            String ownerId,
            Function<Class<?>, String> ownerOfType,
            EntityManager sharedEntityManager,
            OwnerPersistenceValidator validator,
            OwnerUnitOfWork unitOfWork,
            PersistenceFailureTranslator failureTranslator) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        this.ownerOfType = Objects.requireNonNull(ownerOfType, "ownerOfType must not be null");
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork must not be null");
        this.repositoryFactory = new JpaRepositoryFactory(
            Objects.requireNonNull(sharedEntityManager, "sharedEntityManager must not be null"));
        this.repositoryFactory.addRepositoryProxyPostProcessor((proxyFactory, repositoryInformation) ->
            proxyFactory.addAdvice(new OwnerRepositoryInterceptor(ownerId, failureTranslator)));
    }

    @Override
    public <R> R repository(Class<R> repositoryType) {
        Objects.requireNonNull(repositoryType, "repositoryType must not be null");
        return repositoryType.cast(repositories.computeIfAbsent(repositoryType, this::createRepository));
    }

    @Override
    public UnitOfWork unitOfWork() {
        return unitOfWork;
    }

    @Override
    public String toString() {
        return "JpaOwnerPersistence[" + ownerId + "]";
    }

    private Object createRepository(Class<?> repositoryType) {
        if (!ownerId.equals(ownerOfType.apply(repositoryType))) {
            throw new PersistenceCapabilityException(
                PersistenceCapabilityException.Code.REPOSITORY_REJECTED,
                Map.of("owner", ownerId),
                new IllegalStateException("Owner '" + ownerId + "' persistence rejected: "
                    + repositoryType.getName() + " is not packaged with this owner"));
        }
        validator.validateRepository(repositoryType);
        return repositoryFactory.getRepository(repositoryType);
    }
}
