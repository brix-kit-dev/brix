/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa.tenancy;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PreDeleteEvent;
import org.hibernate.event.spi.PreDeleteEventListener;
import org.hibernate.event.spi.PreInsertEvent;
import org.hibernate.event.spi.PreInsertEventListener;
import org.hibernate.event.spi.PreUpdateEvent;
import org.hibernate.event.spi.PreUpdateEventListener;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.hibernate.metamodel.mapping.BasicValuedModelPart;
import org.hibernate.persister.entity.EntityPersister;

/**
 * P0 prototype of the adapter's write check: entity inserts, updates and deletes must stay in
 * the bound tenant set and must not move a row to another tenant. Hibernate writes managed
 * entities by primary key without a tenant predicate, so this check carries their isolation.
 */
public final class P0WriteGuardIntegrator implements Integrator {

    @Override
    public void integrate(Metadata metadata, BootstrapContext bootstrapContext, SessionFactoryImplementor sessionFactory) {
        Object enabled = bootstrapContext.getConfigurationService().getSettings()
            .get(P0TenantFilterContributor.ENABLED);
        if (!"true".equals(String.valueOf(enabled))) {
            return;
        }
        EventListenerRegistry listeners = sessionFactory.getServiceRegistry().requireService(EventListenerRegistry.class);
        WriteGuard guard = new WriteGuard();
        listeners.appendListeners(EventType.PRE_INSERT, guard);
        listeners.appendListeners(EventType.PRE_UPDATE, guard);
        listeners.appendListeners(EventType.PRE_DELETE, guard);
    }

    /** Raised instead of vetoing: a vetoed insert would be dropped without an error. */
    static final class P0ScopeViolation extends RuntimeException {

        private static final long serialVersionUID = 1L;

        P0ScopeViolation(String message) {
            super(message);
        }
    }

    private static final class WriteGuard
            implements PreInsertEventListener, PreUpdateEventListener, PreDeleteEventListener {

        private static final long serialVersionUID = 1L;

        @Override
        public boolean onPreInsert(PreInsertEvent event) {
            check(event.getPersister(), event.getState(), null, "insert");
            return false;
        }

        @Override
        public boolean onPreUpdate(PreUpdateEvent event) {
            check(event.getPersister(), event.getState(), event.getOldState(), "update");
            return false;
        }

        @Override
        public boolean onPreDelete(PreDeleteEvent event) {
            check(event.getPersister(), event.getDeletedState(), null, "delete");
            return false;
        }

        private static void check(EntityPersister persister, Object[] state, Object[] oldState, String operation) {
            int position = tenantPosition(persister);
            Set<Long> scope = P0Scope.current();
            if (position < 0 || scope == null) {
                return;
            }
            Object tenant = state[position];
            if (!scope.contains(tenant)) {
                throw new P0ScopeViolation(operation + " of " + persister.getEntityName() + " outside the bound tenants");
            }
            if (oldState != null && !Objects.equals(oldState[position], tenant)) {
                throw new P0ScopeViolation(operation + " of " + persister.getEntityName() + " changes its tenant");
            }
        }

        private static int tenantPosition(EntityPersister persister) {
            for (int i = 0; i < persister.getNumberOfAttributeMappings(); i++) {
                AttributeMapping attribute = persister.getAttributeMapping(i);
                if (attribute instanceof BasicValuedModelPart basic
                        && "tenant_id".equals(basic.getSelectionExpression().toLowerCase(Locale.ROOT))) {
                    return attribute.getStateArrayPosition();
                }
            }
            return -1;
        }
    }
}
