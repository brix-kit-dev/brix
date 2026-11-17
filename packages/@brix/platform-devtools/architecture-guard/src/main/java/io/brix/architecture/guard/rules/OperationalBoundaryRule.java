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
package io.brix.architecture.guard.rules;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import io.brix.architecture.guard.ArchitectureLayers;

/**
 * What a {@code moduleKind=platform-operational} module may reach besides L2A: the restricted L2B operational SPI and
 * the Data Owners' internal contracts (root BRX-RS-LAYER-015, LAYER-016; super-admin BRX-SA-OWNER-001).
 *
 * <p>Both rules check every class the module's test imports and fail when it imports none. The test imports the
 * module's own classes and nothing else; {@link io.brix.architecture.guard.profiles.OperationalProfile} runs them.</p>
 *
 * @author Brix Architecture Team
 * @since 3.2.0
 */
public final class OperationalBoundaryRule {

    /** The Runtime implementation (L2B). */
    public static final String RUNTIME = "io.runtime.orchestrator..";

    /** The restricted, versioned L2B operational SPI: descriptor binding, operational context, handler factories. */
    public static final String OPERATIONAL_SPI = "io.runtime.orchestrator.operational..";

    /**
     * What a platform module offers other modules: a Data Owner's internal contracts and its versioned api, such as
     * {@code io.brix.platform.tenant.internal} and {@code io.brix.platform.audit.api}.
     */
    public static final String[] OWNER_CONTRACTS = {"io.brix.platform.*.internal..", "io.brix.platform.*.api.."};

    private OperationalBoundaryRule() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Operational code reaches the Runtime only through the operational SPI (root BRX-RS-LAYER-016).
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule runtimeOnlyThroughOperationalSpi() {
        return noClasses()
                .should().dependOnClassesThat(resideInAPackage(RUNTIME)
                        .and(DescribedPredicate.not(resideInAPackage(OPERATIONAL_SPI))))
                .because("root BRX-RS-LAYER-016: a platform-operational module registers its handlers, reads the "
                        + "Runtime's operational state and calls internal contracts only through the restricted L2B "
                        + "operational SPI (" + OPERATIONAL_SPI + ")");
    }

    /**
     * Operational code reaches other platform modules only through a Data Owner's internal contracts and published
     * api (root BRX-RS-LAYER-015, LAYER-016; super-admin BRX-SA-OWNER-001): never through an Owner's repository,
     * entity, service or configuration, nor through a platform library.
     *
     * <p>The module is the set of classes the rule checks. The condition reports each dependency as a violation, so
     * the rule is {@code classes().should(...)}: under {@code noClasses()} ArchUnit inverts a condition's events.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule ownersOnlyThroughContracts() {
        return classes()
                .should(new OwnerContractsOnly())
                .because("root BRX-RS-LAYER-015 and LAYER-016, super-admin BRX-SA-OWNER-001: an operational module "
                        + "reaches platform data only through the Data Owner's internal contracts, never through its "
                        + "Repository, Entity, Service or any other implementation class");
    }

    /**
     * Reports each dependency on a platform class that is neither part of the checked module nor in a Data Owner's
     * internal contract or api package.
     */
    private static final class OwnerContractsOnly extends ArchCondition<JavaClass> {

        private static final DescribedPredicate<JavaClass> PLATFORM =
                resideInAPackage(ArchitectureLayers.PLATFORM_ROOT);

        private static final DescribedPredicate<JavaClass> CONTRACT = resideInAnyPackage(OWNER_CONTRACTS);

        private Set<String> module = Set.of();

        OwnerContractsOnly() {
            super("reach other platform modules only through a Data Owner's internal contracts or published api");
        }

        @Override
        public void init(Collection<JavaClass> allObjectsToTest) {
            module = allObjectsToTest.stream().map(JavaClass::getName).collect(Collectors.toUnmodifiableSet());
        }

        @Override
        public void check(JavaClass origin, ConditionEvents events) {
            for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                JavaClass type = target.isArray() ? target.getBaseComponentType() : target;
                if (PLATFORM.test(type) && !CONTRACT.test(type) && !module.contains(type.getName())) {
                    events.add(SimpleConditionEvent.violated(dependency, String.format(
                            "%s: %s is neither part of this module nor a Data Owner's internal contract or api",
                            dependency.getDescription(), type.getName())));
                }
            }
        }
    }
}
