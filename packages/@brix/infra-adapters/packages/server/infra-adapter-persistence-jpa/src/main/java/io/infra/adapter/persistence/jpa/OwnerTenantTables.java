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

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.hibernate.metamodel.mapping.BasicEntityIdentifierMapping;
import org.hibernate.metamodel.mapping.BasicValuedModelPart;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.hibernate.persister.entity.EntityPersister;

import io.runtime.orchestrator.persistence.TenantIsolationRegister;
import io.runtime.sdk.capability.PersistenceCapabilityException;

/**
 * How the tenant filter treats each table of one owner (multi-tenant 5.1.2 §6.1).
 *
 * <p>A table with {@code tenant_id} is a tenant table and must not allow nulls there. A table
 * without it must be a listed global table or an outbox or inbox the owner declares. Anything
 * else fails startup, so a business table that lost its tenant column cannot pass as global.</p>
 */
final class OwnerTenantTables {

    /** Classification of a table. */
    enum Kind {
        /** Rows belong to one tenant each; reads and writes stay inside the bound tenants. */
        TENANT,
        /** Platform-wide rows listed in §6.1. */
        GLOBAL,
        /** Outbox or inbox records the owner declares; the tenant column is optional. */
        CUSTODIAN
    }

    private final String ownerId;
    private final Map<String, Kind> tables;
    private final Map<String, String> tenantAttributes;

    private OwnerTenantTables(String ownerId, Map<String, Kind> tables, Map<String, String> tenantAttributes) {
        this.ownerId = ownerId;
        this.tables = Map.copyOf(tables);
        this.tenantAttributes = Map.copyOf(tenantAttributes);
    }

    static OwnerTenantTables none(String ownerId) {
        return new OwnerTenantTables(ownerId, Map.of(), Map.of());
    }

    /**
     * Classifies the tables of an owner's entities.
     *
     * @param ownerId owner id
     * @param ownerTypes the owner's managed types
     * @param entityManagerFactory persistence unit that maps the types
     * @param globalTables §6.1 global tables
     * @param custodianTables outbox and inbox tables the owner declares
     * @return the classification
     * @throws PersistenceCapabilityException when a table cannot be classified
     */
    static OwnerTenantTables classify(
            String ownerId,
            Set<Class<?>> ownerTypes,
            EntityManagerFactory entityManagerFactory,
            Set<String> globalTables,
            Set<String> custodianTables) {
        Map<String, Kind> tables = new LinkedHashMap<>();
        Map<String, String> tenantAttributes = new LinkedHashMap<>();
        SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        for (Class<?> type : ownerTypes) {
            if (!sessionFactory.getJpaMetamodel().getEntities().stream()
                    .anyMatch(entity -> entity.getJavaType() == type)) {
                continue;
            }
            EntityPersister persister = sessionFactory.getMappingMetamodel().getEntityDescriptor(type);
            String table = tableOf(persister);
            Optional<BasicValuedModelPart> tenantColumn = tenantColumn(persister);
            Kind kind;
            if (custodianTables.contains(table)) {
                kind = Kind.CUSTODIAN;
            } else if (tenantColumn.isPresent()) {
                if (tenantColumn.get().isNullable()) {
                    throw rejected(ownerId, type.getName() + " maps a nullable tenant_id; tenant tables need "
                        + "tenant_id NOT NULL (multi-tenant T-1)");
                }
                if (persister.canReadFromCache() || persister.canWriteToCache()) {
                    throw rejected(ownerId, type.getName() + " caches tenant rows in the second-level cache, "
                        + "which the tenant filter does not see");
                }
                kind = Kind.TENANT;
                String attribute = ((AttributeMapping) tenantColumn.get()).getAttributeName();
                tenantAttributes.put(persister.getEntityName(), attribute);
                tenantAttributes.put(persister.getJpaEntityName().toLowerCase(Locale.ROOT), attribute);
            } else if (globalTables.contains(table)) {
                kind = Kind.GLOBAL;
            } else {
                throw rejected(ownerId, type.getName() + " maps table " + table
                    + ", which has no tenant_id and is not a registered global table (multi-tenant §6.1)");
            }
            Kind previous = tables.putIfAbsent(table, kind);
            if (previous != null && previous != kind) {
                throw rejected(ownerId, "table " + table + " is mapped both as " + previous + " and " + kind);
            }
        }
        return new OwnerTenantTables(ownerId, tables, tenantAttributes);
    }

    String ownerId() {
        return ownerId;
    }

    /**
     * Returns how the table is treated, when the owner maps it.
     *
     * @param table table name in any case
     * @return the classification
     */
    Optional<Kind> kindOf(String table) {
        return Optional.ofNullable(tables.get(normalize(table)));
    }

    boolean isTenantTable(String table) {
        return kindOf(table).filter(Kind.TENANT::equals).isPresent();
    }

    Set<String> tenantTables() {
        return tables.entrySet().stream()
            .filter(entry -> entry.getValue() == Kind.TENANT)
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * Returns the attribute that maps {@code tenant_id} on an entity of a tenant table.
     *
     * @param entityName Hibernate entity name, or the JPA entity name a query uses
     * @return the attribute name
     */
    Optional<String> tenantAttribute(String entityName) {
        String attribute = tenantAttributes.get(entityName);
        return Optional.ofNullable(attribute != null ? attribute : tenantAttributes.get(entityName.toLowerCase(Locale.ROOT)));
    }

    static String tableOf(EntityPersister persister) {
        if (persister instanceof AbstractEntityPersister entityPersister) {
            return normalize(entityPersister.getTableName());
        }
        return normalize(persister.getEntityName());
    }

    static Optional<BasicValuedModelPart> tenantColumn(EntityPersister persister) {
        if (identifierIsTenantColumn(persister)) {
            return Optional.of((BasicEntityIdentifierMapping) persister.getIdentifierMapping());
        }
        for (int i = 0; i < persister.getNumberOfAttributeMappings(); i++) {
            AttributeMapping attribute = persister.getAttributeMapping(i);
            if (attribute instanceof BasicValuedModelPart basic && isTenantColumn(basic)) {
                return Optional.of(basic);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns whether the entity's identifier is its tenant column, as in a projection that holds
     * one row per tenant. Its tenant is then the entity id, not a value of the state array.
     *
     * @param persister entity persister
     * @return {@code true} when {@code tenant_id} is the identifier
     */
    static boolean identifierIsTenantColumn(EntityPersister persister) {
        return persister.getIdentifierMapping() instanceof BasicEntityIdentifierMapping identifier
            && isTenantColumn(identifier);
    }

    private static boolean isTenantColumn(BasicValuedModelPart part) {
        return TenantIsolationRegister.TENANT_COLUMN.equals(normalize(part.getSelectionExpression()));
    }

    static String normalize(String identifier) {
        String value = Objects.requireNonNull(identifier, "identifier must not be null").toLowerCase(Locale.ROOT);
        int dot = value.lastIndexOf('.');
        value = dot >= 0 ? value.substring(dot + 1) : value;
        return value.replace("\"", "").replace("`", "");
    }

    private static PersistenceCapabilityException rejected(String ownerId, String detail) {
        return new PersistenceCapabilityException(
            PersistenceCapabilityException.Code.REPOSITORY_REJECTED,
            Map.of("owner", ownerId),
            new IllegalStateException("Owner '" + ownerId + "' persistence rejected: " + detail));
    }
}
