/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Plugins do not take the Host's event bus from {@code RuntimeContext.getEventBus()} (Kafka design §6; standard-link
 * plan L2.3). A plugin publishes through the bus its {@code PluginContext} gives it: CRITICAL and STANDARD events go
 * to its own outbox in its unit of work.
 *
 * <p>The classes that called it when this rule came in are listed, one fully qualified class name per line, in
 * {@value #KNOWN_CALLERS} resources on the test class path of the module that checks them. A listed class and its
 * nested classes may keep calling it. The lists only shrink: the plugin migration removes a class from its list when it
 * migrates the class, and the last migrated plugin empties them before the method is removed.</p>
 */
public final class NoLegacyEventBusRule {

    /** Where a module lists its classes that still call the legacy bus. */
    public static final String KNOWN_CALLERS = "META-INF/brix/guard/legacy-event-bus-callers.txt";

    private static final String RUNTIME_CONTEXT = "io.runtime.sdk.context.RuntimeContext";

    private NoLegacyEventBusRule() {}

    /**
     * Returns the rule, with the known callers listed on the current test class path.
     *
     * @return the rule
     */
    public static ArchRule rule() {
        Set<String> known = knownCallers(classLoader());
        return noClasses()
                .that(DescribedPredicate.<JavaClass>describe(
                        "are not listed in " + KNOWN_CALLERS, type -> !listed(type.getName(), known)))
                .should().callMethodWhere(DescribedPredicate.<JavaMethodCall>describe(
                        "the target is RuntimeContext.getEventBus()",
                        call -> "getEventBus".equals(call.getTarget().getName())
                                && call.getTarget().getOwner().isAssignableTo(RUNTIME_CONTEXT)))
                .because("RuntimeContext.getEventBus() is retired: publish through the EventBusCapability the "
                        + "plugin's PluginContext gives it (Kafka design §6). A class that still calls it is listed in "
                        + KNOWN_CALLERS + " until it migrates; the list only shrinks.")
                .allowEmptyShould(true);
    }

    /**
     * Returns the known callers every {@value #KNOWN_CALLERS} resource visible to {@code loader} lists.
     *
     * @param loader the loader of the module's test class path
     * @return the listed class names
     */
    static Set<String> knownCallers(ClassLoader loader) {
        Set<String> known = new TreeSet<>();
        try {
            for (URL resource : Collections.list(loader.getResources(KNOWN_CALLERS))) {
                try (BufferedReader lines = new BufferedReader(
                        new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8))) {
                    lines.lines()
                            .map(String::trim)
                            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                            .forEach(known::add);
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot read " + KNOWN_CALLERS, failure);
        }
        return known;
    }

    private static boolean listed(String className, Set<String> known) {
        if (known.contains(className)) {
            return true;
        }
        int nested = className.indexOf('$');
        return nested > 0 && known.contains(className.substring(0, nested));
    }

    private static ClassLoader classLoader() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        return context != null ? context : NoLegacyEventBusRule.class.getClassLoader();
    }
}
