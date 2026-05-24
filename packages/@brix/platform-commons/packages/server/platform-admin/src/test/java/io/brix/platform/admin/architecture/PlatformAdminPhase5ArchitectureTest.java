package io.brix.platform.admin.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * Phase 5 architecture guard for the platform super-admin v2.0 red lines.
 */
@AnalyzeClasses(
        packages = {
                "io.brix.platform.admin",
                "io.brix.platform.tenant",
                "io.brix.platform.auth",
                "io.runtime.sdk.capability"
        },
        importOptions = ImportOption.DoNotIncludeTests.class
)
    @SuppressWarnings("unused")
class PlatformAdminPhase5ArchitectureTest {

    @ArchTest
    static final ArchRule identityStatusUsesDedicatedEnum = classes()
            .that().haveSimpleName("Identity")
            .should(haveFieldType("status", "io.brix.platform.tenant.enums.IdentityStatus"))
            .because("SSOT §16.1 requires Identity.status to use IdentityStatus, not tenant MemberStatus");

    @ArchTest
    static final ArchRule platformAdminStatusUsesRevokedEnum = classes()
            .that().haveSimpleName("PlatformAdmin")
            .should(haveFieldType("status", "io.brix.platform.tenant.enums.PlatformAdminStatus"))
            .because("SSOT §16.2 requires PlatformAdmin.status to use ACTIVE/REVOKED semantics");

    @ArchTest
    static final ArchRule platformAdminCodeDoesNotExposeDisabledGrantNames = classes()
            .that().resideInAnyPackage("io.brix.platform.admin..", "io.brix.platform.tenant..")
            .should(notDeclareDisabledGrantMembers())
            .because("Phase 5 §5-2 forbids disabled_* platform-admin grant names after REVOKED migration");

    @ArchTest
    static final ArchRule loginResponsesDoNotExposePlatformAdminBoolean = classes()
            .should(notExposeMemberNamed("platform" + "Admin" + "Mode"))
            .because("SSOT R-14 requires platform identity to be derived from scope, never a boolean response flag");

    private static ArchCondition<JavaClass> haveFieldType(String fieldName, String expectedType) {
        return new ArchCondition<JavaClass>("declare field " + fieldName + " as " + expectedType) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (JavaField field : item.getFields()) {
                    if (field.getName().equals(fieldName)) {
                        String actualType = field.getRawType().getFullName();
                        boolean matches = expectedType.equals(actualType);
                        events.add(new SimpleConditionEvent(
                                field,
                                matches,
                                item.getFullName() + "." + fieldName + " has type " + actualType));
                        return;
                    }
                }
                events.add(SimpleConditionEvent.violated(
                        item,
                        item.getFullName() + " does not declare field " + fieldName));
            }
        };
    }

    private static ArchCondition<JavaClass> notDeclareDisabledGrantMembers() {
        return new ArchCondition<JavaClass>("not declare disabled platform-admin grant members") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (JavaField field : item.getFields()) {
                    if (containsDisabledGrantName(field.getName())) {
                        events.add(SimpleConditionEvent.violated(
                                field,
                                item.getFullName() + " declares legacy grant field " + field.getName()));
                    }
                }
                for (JavaMethod method : item.getMethods()) {
                    if (containsDisabledGrantName(method.getName())) {
                        events.add(SimpleConditionEvent.violated(
                                method,
                                item.getFullName() + " declares legacy grant method " + method.getName()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> notExposeMemberNamed(String forbiddenName) {
        return new ArchCondition<JavaClass>("not expose member named " + forbiddenName) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (!isLoginResponseShape(item)) {
                    return;
                }
                for (JavaField field : item.getFields()) {
                    if (field.getName().equals(forbiddenName)) {
                        events.add(SimpleConditionEvent.violated(
                                field,
                                item.getFullName() + " declares forbidden field " + forbiddenName));
                    }
                }
                for (JavaMethod method : item.getMethods()) {
                    if (method.getName().equals(forbiddenName)) {
                        events.add(SimpleConditionEvent.violated(
                                method,
                                item.getFullName() + " declares forbidden accessor " + forbiddenName));
                    }
                }
            }
        };
    }

    private static boolean isLoginResponseShape(JavaClass item) {
        String simpleName = item.getSimpleName();
        return simpleName.contains("LoginResult")
                || simpleName.contains("LoginResponse")
                || simpleName.contains("LoginResponseDto")
                || simpleName.contains("PlatformLoginResponse");
    }

    private static boolean containsDisabledGrantName(String name) {
        String lower = name.toLowerCase();
        return lower.contains("disabledat")
                || lower.contains("disabledby")
                || lower.contains("disablereason");
    }
}