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
package io.infra.adapter.persistence.jpa.config;

import java.lang.reflect.Member;

import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.engine.spi.SessionImplementor;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.ClassUtils;

import io.infra.adapter.persistence.jpa.OwnerPersistenceUnits;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.sdk.capability.PersistenceCapability;

/**
 * Provides owner-scoped persistence on top of the host's JPA persistence unit.
 *
 * <p>The runtime obtains owner persistence through {@link OwnerPersistenceProvider}. Platform modules
 * that are still assembled by the framework container receive their owner's
 * {@link PersistenceCapability} through injection; the owner is derived from the artifact of the
 * injecting class, so a module can only ever receive its own owner's capability.</p>
 */
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass({EntityManagerFactory.class, JpaRepositoryFactory.class, SessionImplementor.class})
@ConditionalOnBean({EntityManagerFactory.class, PlatformTransactionManager.class, DataSource.class})
@ConditionalOnProperty(prefix = "brix.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PersistenceProperties.class)
public class JpaPersistenceAutoConfiguration {

    /**
     * Owner persistence registry, also exposed to the runtime as {@link OwnerPersistenceProvider}.
     *
     * @param entityManagerFactory shared persistence unit
     * @param transactionManager JPA transaction manager of the shared persistence unit
     * @param dataSource data source of the shared persistence unit
     * @param properties adapter settings
     * @return the owner persistence registry
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(OwnerPersistenceProvider.class)
    public OwnerPersistenceUnits ownerPersistenceUnits(
            EntityManagerFactory entityManagerFactory,
            PlatformTransactionManager transactionManager,
            DataSource dataSource,
            PersistenceProperties properties) {
        if (!(transactionManager instanceof JpaTransactionManager jpaTransactionManager)) {
            throw new IllegalStateException("Owner persistence requires a JpaTransactionManager, found "
                + transactionManager.getClass().getName());
        }
        return new OwnerPersistenceUnits(
            entityManagerFactory,
            jpaTransactionManager,
            dataSource,
            properties.getTransactionTimeout(),
            properties.isSchemaValidation(),
            ClassUtils.getDefaultClassLoader());
    }

    /**
     * The persistence capability of the owner that packages the injecting class.
     *
     * @param injectionPoint where the capability is injected
     * @param ownerPersistenceUnits owner persistence registry
     * @return the injecting owner's persistence capability
     */
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public PersistenceCapability ownerScopedPersistenceCapability(
            InjectionPoint injectionPoint,
            OwnerPersistenceUnits ownerPersistenceUnits) {
        Member member = injectionPoint.getMember();
        return ownerPersistenceUnits.forComponent(member.getDeclaringClass());
    }
}
