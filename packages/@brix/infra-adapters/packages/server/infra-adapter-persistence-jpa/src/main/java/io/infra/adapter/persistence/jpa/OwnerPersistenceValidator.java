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

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import javax.sql.DataSource;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.NamedNativeQueries;
import jakarta.persistence.NamedNativeQuery;
import jakarta.persistence.NamedStoredProcedureQueries;
import jakarta.persistence.NamedStoredProcedureQuery;
import jakarta.persistence.SqlResultSetMapping;
import jakarta.persistence.SqlResultSetMappings;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.AbstractRepositoryMetadata;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import io.runtime.sdk.capability.PersistenceCapabilityException;

/**
 * Proves the persistence rules of one owner at startup.
 *
 * <p>The proof does not rely on naming conventions. Hibernate builds a validation persistence unit that
 * contains only the owner's managed types: an association to another owner's entity fails while that
 * unit is built, a JPQL query that names another owner's entity fails when the repository is created
 * against it, and the owner's tables are checked against the migrated schema (root blueprint
 * BRX-RS-DATA-026, DATA-027 and DATA-031). The persistence API whitelist of DATA-024, DATA-025 and
 * DATA-037 is checked by reflection.</p>
 */
final class OwnerPersistenceValidator implements AutoCloseable {

    private static final String NATIVE_QUERY_ANNOTATION = "org.springframework.data.jpa.repository.NativeQuery";
    private static final String HIBERNATE_ANNOTATION_PACKAGE = "org.hibernate.annotations";
    private static final List<Class<? extends Annotation>> NATIVE_MAPPING_ANNOTATIONS = List.of(
        NamedNativeQuery.class,
        NamedNativeQueries.class,
        NamedStoredProcedureQuery.class,
        NamedStoredProcedureQueries.class,
        SqlResultSetMapping.class,
        SqlResultSetMappings.class);
    private static final List<String> NON_INHERITED_SETTING_PREFIXES = List.of(
        AvailableSettings.HBM2DDL_AUTO,
        "jakarta.persistence.schema-generation",
        "javax.persistence.schema-generation",
        AvailableSettings.SCANNER,
        AvailableSettings.SCANNER_DISCOVERY,
        AvailableSettings.SESSION_FACTORY_NAME,
        AvailableSettings.PERSISTENCE_UNIT_NAME,
        AvailableSettings.DATASOURCE,
        AvailableSettings.JAKARTA_JTA_DATASOURCE,
        AvailableSettings.JAKARTA_NON_JTA_DATASOURCE);

    private final String ownerId;
    private final Set<Class<?>> ownerTypes;
    private final EntityManagerFactory validationEntityManagerFactory;

    private OwnerPersistenceValidator(
            String ownerId,
            Set<Class<?>> ownerTypes,
            EntityManagerFactory validationEntityManagerFactory) {
        this.ownerId = ownerId;
        this.ownerTypes = ownerTypes;
        this.validationEntityManagerFactory = validationEntityManagerFactory;
    }

    /**
     * Checks the owner's managed types and builds its validation persistence unit.
     *
     * @param ownerId data owner id
     * @param ownerTypes the owner's entities, embeddables and mapped superclasses
     * @param applicationEntityManagerFactory the shared persistence unit whose settings are reused
     * @param dataSource data source used for schema validation
     * @param validateSchema whether the owner's tables are checked against the database
     * @return the validator
     */
    static OwnerPersistenceValidator create(
            String ownerId,
            Set<Class<?>> ownerTypes,
            EntityManagerFactory applicationEntityManagerFactory,
            DataSource dataSource,
            boolean validateSchema) {
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Set<Class<?>> types = Set.copyOf(Objects.requireNonNull(ownerTypes, "ownerTypes must not be null"));
        types.forEach(type -> checkManagedType(ownerId, type));
        if (types.isEmpty()) {
            return new OwnerPersistenceValidator(ownerId, types, null);
        }
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setPersistenceUnitName("brix-owner-validation:" + ownerId);
        factory.setDataSource(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        factory.setManagedTypes(PersistenceManagedTypes.of(
            types.stream().map(Class::getName).sorted().toList(),
            List.of()));
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(validationSettings(applicationEntityManagerFactory, validateSchema));
        factory.setBeanClassLoader(types.iterator().next().getClassLoader());
        try {
            factory.afterPropertiesSet();
        } catch (RuntimeException failure) {
            throw rejected(ownerId, "owner persistence unit failed validation", failure);
        }
        return new OwnerPersistenceValidator(ownerId, types, factory.getObject());
    }

    /**
     * Rejects a repository interface that breaks the owner boundary or the persistence API whitelist.
     *
     * @param repositoryInterface repository interface requested by the owner
     */
    void validateRepository(Class<?> repositoryInterface) {
        if (!repositoryInterface.isInterface() || !Repository.class.isAssignableFrom(repositoryInterface)) {
            throw rejected(ownerId, repositoryInterface.getName()
                + " is not a declarative repository interface", null);
        }
        RepositoryMetadata metadata = AbstractRepositoryMetadata.getMetadata(repositoryInterface);
        if (!ownerTypes.contains(metadata.getDomainType())) {
            throw rejected(ownerId, repositoryInterface.getName() + " manages "
                + metadata.getDomainType().getName() + ", which is not an entity of this owner", null);
        }
        for (Method method : repositoryInterface.getMethods()) {
            checkRepositoryMethod(repositoryInterface, method);
        }
        EntityManager entityManager = validationEntityManagerFactory.createEntityManager();
        try {
            new JpaRepositoryFactory(entityManager).getRepository(repositoryInterface);
        } catch (RuntimeException failure) {
            throw rejected(ownerId, repositoryInterface.getName()
                + " has a query that does not resolve against this owner's entities", failure);
        } finally {
            entityManager.close();
        }
    }

    @Override
    public void close() {
        if (validationEntityManagerFactory != null && validationEntityManagerFactory.isOpen()) {
            validationEntityManagerFactory.close();
        }
    }

    private void checkRepositoryMethod(Class<?> repositoryInterface, Method method) {
        String location = repositoryInterface.getName() + "#" + method.getName();
        Query query = method.getAnnotation(Query.class);
        if (query != null && query.nativeQuery()) {
            throw rejected(ownerId, location + " declares a native query", null);
        }
        if (method.isAnnotationPresent(Procedure.class) || hasAnnotationNamed(method, NATIVE_QUERY_ANNOTATION)) {
            throw rejected(ownerId, location + " declares native SQL or a stored procedure", null);
        }
        Modifying modifying = method.getAnnotation(Modifying.class);
        if (modifying != null && modifying.clearAutomatically() && !modifying.flushAutomatically()) {
            throw rejected(ownerId, location
                + " clears the persistence context without flushing it first", null);
        }
    }

    private static void checkManagedType(String ownerId, Class<?> type) {
        Table table = type.getAnnotation(Table.class);
        if (table != null && (!table.schema().isEmpty() || !table.catalog().isEmpty())) {
            throw rejected(ownerId, type.getName() + " hard-codes a schema or catalog", null);
        }
        for (Class<? extends Annotation> annotation : NATIVE_MAPPING_ANNOTATIONS) {
            if (type.isAnnotationPresent(annotation)) {
                throw rejected(ownerId, type.getName() + " declares native SQL mapping @"
                    + annotation.getSimpleName(), null);
            }
        }
        for (AnnotatedElement element : mappedElements(type)) {
            if (element.isAnnotationPresent(GeneratedValue.class)) {
                throw rejected(ownerId, type.getName()
                    + " generates identifiers in the database; assign them through IdGeneratorCapability", null);
            }
            for (Annotation annotation : element.getAnnotations()) {
                checkVendorAnnotation(ownerId, type, annotation);
            }
        }
        for (Annotation annotation : type.getAnnotations()) {
            checkVendorAnnotation(ownerId, type, annotation);
        }
    }

    private static void checkVendorAnnotation(String ownerId, Class<?> type, Annotation annotation) {
        Class<? extends Annotation> annotationType = annotation.annotationType();
        if (!annotationType.getPackageName().equals(HIBERNATE_ANNOTATION_PACKAGE)) {
            return;
        }
        if (annotation instanceof JdbcTypeCode typeCode && typeCode.value() == SqlTypes.JSON) {
            return;
        }
        throw rejected(ownerId, type.getName() + " uses vendor annotation @" + annotationType.getSimpleName()
            + " outside the persistence API whitelist", null);
    }

    private static List<AnnotatedElement> mappedElements(Class<?> type) {
        List<AnnotatedElement> elements = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            Stream.of(current.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .forEach(elements::add);
            elements.addAll(Arrays.asList(current.getDeclaredMethods()));
        }
        return elements;
    }

    private static boolean hasAnnotationNamed(Method method, String annotationName) {
        return Arrays.stream(method.getAnnotations())
            .anyMatch(annotation -> annotation.annotationType().getName().equals(annotationName));
    }

    private static Map<String, Object> validationSettings(
            EntityManagerFactory applicationEntityManagerFactory,
            boolean validateSchema) {
        Map<String, Object> settings = new HashMap<>();
        applicationEntityManagerFactory.getProperties().forEach((key, value) -> {
            if (value != null && inherited(key)) {
                settings.put(key, value);
            }
        });
        settings.put(AvailableSettings.HBM2DDL_AUTO, validateSchema ? "validate" : "none");
        return settings;
    }

    private static boolean inherited(String key) {
        boolean persistenceSetting = key.startsWith("hibernate.")
            || key.startsWith("jakarta.persistence.")
            || key.startsWith("javax.persistence.");
        return persistenceSetting && NON_INHERITED_SETTING_PREFIXES.stream().noneMatch(key::startsWith);
    }

    private static PersistenceCapabilityException rejected(String ownerId, String detail, Throwable cause) {
        IllegalStateException diagnostic = new IllegalStateException(
            "Owner '" + ownerId + "' persistence rejected: " + detail, cause);
        return new PersistenceCapabilityException(
            PersistenceCapabilityException.Code.REPOSITORY_REJECTED,
            Map.of("owner", ownerId),
            diagnostic);
    }
}
