/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa.tenancy;

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

/**
 * P0 prototype of the adapter's tenant filters: registers them on entities that only carry
 * plugin-whitelisted annotations. Hibernate runs additional mapping contributors after the
 * annotated entities are bound and before the persisters are built, so filters added to the
 * entity and collection bindings here are part of the runtime model.
 */
public final class P0TenantFilterContributor implements AdditionalMappingContributor {

    /** Only P0 environments opt in, so the module's other persistence tests stay unfiltered. */
    static final String ENABLED = "brix.p0.tenancy";

    static final String TENANT_FILTER = "brix_tenant";
    static final String TENANT_PARAMETER = "brixTenantIds";
    static final String IDENTITY_FILTER = "brix_identity";
    static final String IDENTITY_PARAMETER = "brixIdentityId";
    static final String INVITEE_FILTER = "brix_invitee";
    static final String INVITEE_PARAMETER = "brixInviteeEmail";

    private static final String TENANT_CONDITION = "tenant_id in (:" + TENANT_PARAMETER + ")";
    private static final String IDENTITY_CONDITION = "identity_id = :" + IDENTITY_PARAMETER;
    private static final String INVITEE_CONDITION = "invitee_email = :" + INVITEE_PARAMETER;

    @Override
    public String getContributorName() {
        return "brix-p0-tenancy";
    }

    @Override
    public void contribute(
            AdditionalMappingContributions contributions,
            InFlightMetadataCollector metadata,
            ResourceStreamLocator resourceStreamLocator,
            MetadataBuildingContext buildingContext) {
        Object enabled = metadata.getBootstrapContext().getConfigurationService().getSettings().get(ENABLED);
        if (!"true".equals(String.valueOf(enabled))) {
            return;
        }
        JdbcMapping longType = metadata.getTypeConfiguration().getBasicTypeForJavaType(Long.class);
        JdbcMapping stringType = metadata.getTypeConfiguration().getBasicTypeForJavaType(String.class);
        define(metadata, TENANT_FILTER, TENANT_CONDITION, TENANT_PARAMETER, longType);
        define(metadata, IDENTITY_FILTER, IDENTITY_CONDITION, IDENTITY_PARAMETER, longType);
        define(metadata, INVITEE_FILTER, INVITEE_CONDITION, INVITEE_PARAMETER, stringType);

        for (PersistentClass entity : metadata.getEntityBindings()) {
            Table table = entity.getTable();
            if (hasColumn(table, "tenant_id")) {
                entity.addFilter(TENANT_FILTER, TENANT_CONDITION, true, Map.of(), Map.of());
            }
            if (hasColumn(table, "identity_id")) {
                entity.addFilter(IDENTITY_FILTER, IDENTITY_CONDITION, true, Map.of(), Map.of());
            }
            if (hasColumn(table, "invitee_email")) {
                entity.addFilter(INVITEE_FILTER, INVITEE_CONDITION, true, Map.of(), Map.of());
            }
        }
        for (Collection collection : metadata.getCollectionBindings()) {
            if (collection.getElement() instanceof OneToMany element
                    && hasColumn(element.getAssociatedClass().getTable(), "tenant_id")) {
                collection.addFilter(TENANT_FILTER, TENANT_CONDITION, true, Map.of(), Map.of());
            }
        }
    }

    private static void define(
            InFlightMetadataCollector metadata,
            String name,
            String condition,
            String parameter,
            JdbcMapping parameterType) {
        // Not auto-enabled: the unit of work enables it with the bound scope. Load by key is
        // filtered too, so find(), references and lazy to-one loads cannot reach other tenants.
        metadata.addFilterDefinition(new FilterDefinition(
            name, condition, false, true, Map.of(parameter, parameterType), Map.of()));
    }

    private static boolean hasColumn(Table table, String column) {
        return table.getColumns().stream()
            .anyMatch(candidate -> candidate.getName().toLowerCase(Locale.ROOT).equals(column));
    }
}
