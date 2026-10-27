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

import java.util.Locale;
import java.util.Map;

import org.hibernate.boot.ResourceStreamLocator;
import org.hibernate.boot.spi.AdditionalMappingContributions;
import org.hibernate.boot.spi.AdditionalMappingContributor;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.spi.FilterDefinition;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.OneToMany;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Table;
import org.hibernate.metamodel.mapping.JdbcMapping;

import io.runtime.orchestrator.persistence.TenantAccessPolicy;
import io.runtime.orchestrator.persistence.TenantIsolationRegister;

/**
 * Registers the tenant filter on every entity and collection whose table has {@code tenant_id},
 * plus the key filters of the registered cross-tenant policies (root BRX-RS-DATA-040,
 * multi-tenant §6.4). Plugin entities carry no Hibernate annotation for it (BRX-RS-DATA-024).
 *
 * <p>Hibernate calls additional mapping contributors after the annotated entities are bound and
 * before it builds the persisters, so the filters added here are part of the runtime model. They
 * are off by default; an owner unit of work turns them on for the scope it is bound to. Load by
 * key is filtered too, so {@code find}, references and lazy to-one loads stay in scope.</p>
 */
public final class TenantFilters implements AdditionalMappingContributor {

    static final String TENANT_FILTER = "brix_tenant";
    static final String TENANT_PARAMETER = "brixTenantIds";
    static final String KEY_PARAMETER = "brixPolicyKey";

    private static final String TENANT_CONDITION =
        TenantIsolationRegister.TENANT_COLUMN + " in (:" + TENANT_PARAMETER + ")";

    @Override
    public String getContributorName() {
        return "brix-tenant-filters";
    }

    @Override
    public void contribute(
            AdditionalMappingContributions contributions,
            InFlightMetadataCollector metadata,
            ResourceStreamLocator resourceStreamLocator,
            MetadataBuildingContext buildingContext) {
        metadata.addFilterDefinition(new FilterDefinition(
            TENANT_FILTER, TENANT_CONDITION, false, true,
            Map.of(TENANT_PARAMETER, jdbcMapping(metadata, Long.class)), Map.of()));
        for (TenantAccessPolicy policy : TenantAccessPolicy.values()) {
            if (policy.keyBoundByRuntime()) {
                metadata.addFilterDefinition(new FilterDefinition(
                    policy.keyFilterName(), keyCondition(policy), false, true,
                    Map.of(KEY_PARAMETER, jdbcMapping(metadata, policy.keyType())), Map.of()));
            }
        }
        for (PersistentClass entity : metadata.getEntityBindings()) {
            Table table = entity.getTable();
            if (hasColumn(table, TenantIsolationRegister.TENANT_COLUMN)) {
                entity.addFilter(TENANT_FILTER, TENANT_CONDITION, true, Map.of(), Map.of());
            }
            for (TenantAccessPolicy policy : TenantAccessPolicy.values()) {
                if (policy.keyBoundByRuntime()
                        && policy.keyedTable().equals(OwnerTenantTables.normalize(table.getName()))
                        && hasColumn(table, policy.keyColumn())) {
                    entity.addFilter(policy.keyFilterName(), keyCondition(policy), true, Map.of(), Map.of());
                }
            }
        }
        for (Collection collection : metadata.getCollectionBindings()) {
            if (collection.getElement() instanceof OneToMany element
                    && hasColumn(element.getAssociatedClass().getTable(), TenantIsolationRegister.TENANT_COLUMN)) {
                collection.addFilter(TENANT_FILTER, TENANT_CONDITION, true, Map.of(), Map.of());
            }
        }
    }

    private static String keyCondition(TenantAccessPolicy policy) {
        return policy.keyColumn() + " = :" + KEY_PARAMETER;
    }

    private static JdbcMapping jdbcMapping(InFlightMetadataCollector metadata, Class<?> javaType) {
        return metadata.getTypeConfiguration().getBasicTypeForJavaType(javaType);
    }

    private static boolean hasColumn(Table table, String column) {
        return table.getColumns().stream()
            .anyMatch(candidate -> candidate.getName().toLowerCase(Locale.ROOT).equals(column));
    }
}
