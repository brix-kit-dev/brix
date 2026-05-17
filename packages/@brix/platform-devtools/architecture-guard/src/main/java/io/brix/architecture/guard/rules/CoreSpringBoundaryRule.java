package io.brix.architecture.guard.rules;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Spring boundary rules for plugin-core modules.
 */
public final class CoreSpringBoundaryRule {

    private static final String SPRING_TRANSACTIONAL = "org.springframework.transaction.annotation.Transactional";
    private static final String JAKARTA_TRANSACTIONAL = "jakarta.transaction.Transactional";
    private static final String SPRING_REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController";
    private static final String SPRING_CONTROLLER = "org.springframework.stereotype.Controller";
    private static final String SPRING_REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping";
    private static final String SPRING_GET_MAPPING = "org.springframework.web.bind.annotation.GetMapping";
    private static final String SPRING_POST_MAPPING = "org.springframework.web.bind.annotation.PostMapping";
    private static final String SPRING_PUT_MAPPING = "org.springframework.web.bind.annotation.PutMapping";
    private static final String SPRING_PATCH_MAPPING = "org.springframework.web.bind.annotation.PatchMapping";
    private static final String SPRING_DELETE_MAPPING = "org.springframework.web.bind.annotation.DeleteMapping";

    private CoreSpringBoundaryRule() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Core modules must not declare HTTP endpoints.
     */
    public static ArchRule noHttpEndpointAnnotationsInCoreModule() {
        return classes()
                .should(new ArchCondition<JavaClass>("not declare HTTP controller or mapping annotations") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        if (isHttpEndpointClass(javaClass)) {
                            events.add(SimpleConditionEvent.violated(
                                    javaClass,
                                    javaClass.getName() + " declares HTTP endpoint annotations in a core module"));
                        }

                        for (JavaMethod method : javaClass.getMethods()) {
                            if (isHttpMappingMethod(method)) {
                                events.add(SimpleConditionEvent.violated(
                                        method,
                                        javaClass.getName() + "." + method.getName()
                                                + " declares HTTP mapping annotations in a core module"));
                            }
                        }
                    }
                })
                .because("R1.1: plugin-core contains application core and persistence contracts; HTTP endpoints belong to -server modules")
                .allowEmptyShould(true);
    }

    /**
     * Core modules must not depend on HTTP binding APIs.
     *
     * <p>JPA and Spring Data repository contracts are allowed by R1.1, but
     * web bindings remain a server-layer concern.</p>
     */
    public static ArchRule noWebBindingDependencyInCore() {
        return noClasses()
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web.bind.annotation..",
                        "org.springframework.web.servlet..",
                        "jakarta.servlet..",
                        "javax.servlet..")
                .because("R1.1: plugin-core may use persistence contracts, but HTTP bindings belong to -server modules")
                .allowEmptyShould(true);
    }

    /**
     * Transaction boundaries in core must live at application boundaries.
     */
    public static ArchRule transactionalOnlyInApplicationBoundary() {
        return classes()
                .should(new ArchCondition<JavaClass>(
                        "place @Transactional only on application boundary classes or methods") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        if (hasTransactionalAnnotation(javaClass) && !isApplicationBoundary(javaClass)) {
                            events.add(SimpleConditionEvent.violated(
                                    javaClass,
                                    javaClass.getName()
                                            + " is annotated with @Transactional outside a service/application boundary"));
                        }

                        for (JavaMethod method : javaClass.getMethods()) {
                            if (hasTransactionalAnnotation(method) && !isApplicationBoundary(javaClass)) {
                                events.add(SimpleConditionEvent.violated(
                                        method,
                                        javaClass.getName() + "." + method.getName()
                                                + " is annotated with @Transactional outside a service/application boundary"));
                            }
                        }
                    }
                })
                .because("R1.1: plugin-core may define transaction boundaries only at application/service boundaries")
                .allowEmptyShould(true);
    }

    private static boolean hasTransactionalAnnotation(JavaClass javaClass) {
        return javaClass.isAnnotatedWith(SPRING_TRANSACTIONAL)
                || javaClass.isAnnotatedWith(JAKARTA_TRANSACTIONAL);
    }

    private static boolean hasTransactionalAnnotation(JavaMethod method) {
        return method.isAnnotatedWith(SPRING_TRANSACTIONAL)
                || method.isAnnotatedWith(JAKARTA_TRANSACTIONAL);
    }

    private static boolean isHttpEndpointClass(JavaClass javaClass) {
        return javaClass.isAnnotatedWith(SPRING_REST_CONTROLLER)
                || javaClass.isAnnotatedWith(SPRING_CONTROLLER)
                || javaClass.isAnnotatedWith(SPRING_REQUEST_MAPPING);
    }

    private static boolean isHttpMappingMethod(JavaMethod method) {
        return method.isAnnotatedWith(SPRING_REQUEST_MAPPING)
                || method.isAnnotatedWith(SPRING_GET_MAPPING)
                || method.isAnnotatedWith(SPRING_POST_MAPPING)
                || method.isAnnotatedWith(SPRING_PUT_MAPPING)
                || method.isAnnotatedWith(SPRING_PATCH_MAPPING)
                || method.isAnnotatedWith(SPRING_DELETE_MAPPING);
    }

    private static boolean isApplicationBoundary(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        String simpleName = javaClass.getSimpleName();
        return packageName.endsWith(".service")
                || packageName.contains(".service.")
                || packageName.endsWith(".impl")
                || packageName.contains(".impl.")
                || packageName.endsWith(".application")
                || packageName.contains(".application.")
                || packageName.endsWith(".event")
                || packageName.contains(".event.")
                || simpleName.endsWith("Service")
                || simpleName.endsWith("ServiceImpl")
                || simpleName.endsWith("EventHandler");
    }
}