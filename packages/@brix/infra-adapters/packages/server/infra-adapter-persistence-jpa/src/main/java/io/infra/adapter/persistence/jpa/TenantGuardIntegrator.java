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

import java.io.Serial;
import java.util.Objects;
import java.util.Optional;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostLoadEvent;
import org.hibernate.event.spi.PostLoadEventListener;
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

import io.runtime.orchestrator.persistence.TenantIsolationRegister;

/**
 * Checks entity writes against the tenant scope of the owner transaction and lets keyed rows
 * bind their tenants to a policy scope.
 *
 * <p>Hibernate writes managed entities by primary key, without a tenant predicate, so these
 * listeners carry the isolation of inserts, updates and deletes: the row must belong to a bound
 * tenant and must not move to another one. A violation raises an error rather than vetoing,
 * because a vetoed insert would be dropped silently. Outside an owner unit of work the
 * listeners do nothing; the statement verifier's legacy rules cover those statements.</p>
 */
public final class TenantGuardIntegrator implements Integrator {

    @Override
    public void integrate(Metadata metadata, BootstrapContext bootstrapContext, SessionFactoryImplementor sessionFactory) {
        EventListenerRegistry listeners = sessionFactory.getServiceRegistry().requireService(EventListenerRegistry.class);
        Guard guard = new Guard();
        listeners.appendListeners(EventType.PRE_INSERT, guard);
        listeners.appendListeners(EventType.PRE_UPDATE, guard);
        listeners.appendListeners(EventType.PRE_DELETE, guard);
        listeners.appendListeners(EventType.POST_LOAD, guard);
    }

    private static final class Guard implements PreInsertEventListener, PreUpdateEventListener,
            PreDeleteEventListener, PostLoadEventListener {

        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public boolean onPreInsert(PreInsertEvent event) {
            current().ifPresent(state -> {
                String table = OwnerTenantTables.tableOf(event.getPersister());
                if (TenantIsolationRegister.TENANT_REGISTRY_TABLE.equals(table) && event.getId() instanceof Long id) {
                    state.bindCreated(id);
                }
                checkWrite(state, event.getPersister(), table, event.getId(), event.getState(), null, "insert");
            });
            return false;
        }

        @Override
        public boolean onPreUpdate(PreUpdateEvent event) {
            current().ifPresent(state -> checkWrite(state, event.getPersister(),
                OwnerTenantTables.tableOf(event.getPersister()), event.getId(), event.getState(), event.getOldState(),
                "update"));
            return false;
        }

        @Override
        public boolean onPreDelete(PreDeleteEvent event) {
            current().ifPresent(state -> checkWrite(state, event.getPersister(),
                OwnerTenantTables.tableOf(event.getPersister()), event.getId(), event.getDeletedState(), null,
                "delete"));
            return false;
        }

        @Override
        public void onPostLoad(PostLoadEvent event) {
            current().ifPresent(state -> {
                EntityPersister persister = event.getPersister();
                String table = OwnerTenantTables.tableOf(persister);
                if (state.policy().filter(policy -> policy.keyedTable().equals(table)).isPresent()) {
                    if (OwnerTenantTables.identifierIsTenantColumn(persister)) {
                        state.bindLoaded(table, event.getId() instanceof Long id ? id : null);
                    } else {
                        int position = tenantPosition(persister);
                        if (position >= 0) {
                            Object tenant = persister.getValue(event.getEntity(), position);
                            state.bindLoaded(table, tenant instanceof Long id ? id : null);
                        }
                    }
                }
            });
        }

        private static Optional<TenantScopeState> current() {
            OwnerTransactionScope scope = OwnerTransactionScope.current();
            return scope == null ? Optional.empty() : Optional.of(scope.tenancy());
        }

        private static void checkWrite(
                TenantScopeState state,
                EntityPersister persister,
                String table,
                Object id,
                Object[] values,
                Object[] previousValues,
                String operation) {
            Optional<OwnerTenantTables.Kind> kind = state.tables().kindOf(table);
            if (kind.isEmpty() || kind.get() == OwnerTenantTables.Kind.GLOBAL) {
                return;
            }
            Object tenant = tenantOf(persister, id, values);
            if (kind.get() == OwnerTenantTables.Kind.CUSTODIAN) {
                if (tenant != null && !state.tenants().contains(tenant)) {
                    throw state.violation(operation + " of a custodian record of another tenant into " + table);
                }
                return;
            }
            if (!state.mayWrite(table)) {
                throw state.tenants().isEmpty()
                    ? state.required(operation + " of " + table + " without a bound tenant")
                    : state.violation(operation + " of " + table + " is outside the scope");
            }
            if (!state.tenants().contains(tenant)) {
                throw state.violation(operation + " of a row of another tenant in " + table);
            }
            if (previousValues != null && !Objects.equals(tenantOf(persister, id, previousValues), tenant)) {
                throw state.violation(operation + " moves a row of " + table + " to another tenant");
            }
        }

        /** The row's tenant: the entity id when {@code tenant_id} is the identifier, else its state value. */
        private static Object tenantOf(EntityPersister persister, Object id, Object[] values) {
            if (OwnerTenantTables.identifierIsTenantColumn(persister)) {
                return id;
            }
            int position = tenantPosition(persister);
            return position < 0 ? null : values[position];
        }

        private static int tenantPosition(EntityPersister persister) {
            for (int i = 0; i < persister.getNumberOfAttributeMappings(); i++) {
                AttributeMapping attribute = persister.getAttributeMapping(i);
                if (attribute instanceof BasicValuedModelPart basic && TenantIsolationRegister.TENANT_COLUMN
                        .equals(OwnerTenantTables.normalize(basic.getSelectionExpression()))) {
                    return attribute.getStateArrayPosition();
                }
            }
            return -1;
        }
    }
}
