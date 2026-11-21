/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaMethodReference;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Plugins send queries and commands only through the clients generated from declared schemas (root A-22): the
 * string gateway belongs to the runtime (BRX-RS-MESSAGE-009, LAYER-006).
 *
 * <p>{@link #noStringCommandGateway()} refuses the string command gateway: a class that uses {@code CommandRequest},
 * or calls or references {@code CommandCapability.submit(CommandRequest)}, which takes the command type as a string
 * and a tenant the caller names. The method is deprecated since 3.3.0 for removal in 4.0.0 (A-20); a plugin sends a
 * command with {@code CommandCapability.client(...)} and the owner's generated {@code CommandType}. The plugin profile
 * runs the rule; when it came in, no plugin of either repository used the gateway, so it lists no known callers
 * (standard-link plan queue item 20, 20a, D168).</p>
 *
 * @author Brix Architecture Team
 * @since 3.3.0
 */
public final class GeneratedContractClientsRule {

    private static final String COMMAND_CAPABILITY = "io.runtime.sdk.capability.CommandCapability";

    private static final String COMMAND_REQUEST = "io.runtime.sdk.command.CommandRequest";

    private GeneratedContractClientsRule() {
    }

    /**
     * Refuses the string command gateway: any use of {@code CommandRequest}, and a call to, or a method reference of,
     * {@code CommandCapability.submit(CommandRequest)}, the capability's only {@code submit}, also on a type that
     * extends {@code CommandCapability}.
     *
     * @return the rule
     */
    public static ArchRule noStringCommandGateway() {
        return noClasses()
                .should().dependOnClassesThat().haveFullyQualifiedName(COMMAND_REQUEST)
                .orShould().accessTargetWhere(DescribedPredicate.<JavaAccess<?>>describe(
                        "the target is CommandCapability.submit(CommandRequest), called or referenced",
                        GeneratedContractClientsRule::isStringSubmit))
                .because("the string command gateway is the runtime's (root A-22, BRX-RS-MESSAGE-009): send a "
                        + "command with CommandCapability.client(...) and the owner's generated CommandType. "
                        + "CommandCapability.submit(CommandRequest) is deprecated since 3.3.0 and removed in 4.0.0");
    }

    private static boolean isStringSubmit(JavaAccess<?> access) {
        return (access instanceof JavaMethodCall || access instanceof JavaMethodReference)
                && "submit".equals(access.getName())
                && access.getTarget().getOwner().isAssignableTo(COMMAND_CAPABILITY);
    }
}
