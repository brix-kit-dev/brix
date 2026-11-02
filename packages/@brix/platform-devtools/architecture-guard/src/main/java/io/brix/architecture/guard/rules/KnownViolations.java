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

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Classes a rule still lets through because they broke it before the rule applied to them.
 *
 * <p>Each module lists its own classes in a test resource, one fully qualified name per line, {@code #} starting a
 * comment; a listed class's nested classes are covered too. The lists only shrink. A list that holds back a red line
 * names its exemption under root BRX-RS-GOVERNANCE-013.</p>
 *
 * @author Brix Architecture Team
 * @since 3.3.0
 */
public final class KnownViolations {

    private KnownViolations() {
    }

    /**
     * Reads every copy of a list resource on the class path.
     *
     * @param resource the resource path, such as {@code META-INF/brix/guard/legacy-event-bus-callers.txt}
     * @param loader the class loader to search
     * @return the listed class names
     */
    public static Set<String> read(String resource, ClassLoader loader) {
        Set<String> known = new TreeSet<>();
        try {
            for (URL copy : Collections.list(loader.getResources(resource))) {
                try (BufferedReader lines = new BufferedReader(
                        new InputStreamReader(copy.openStream(), StandardCharsets.UTF_8))) {
                    lines.lines()
                            .map(String::trim)
                            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                            .forEach(known::add);
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot read " + resource, failure);
        }
        return known;
    }

    /**
     * Selects the classes that are not on a list, read from the current class path.
     *
     * @param resource the list resource
     * @return a predicate for {@code that(...)}
     */
    public static DescribedPredicate<JavaClass> notListedIn(String resource) {
        Set<String> known = read(resource, classLoader());
        return DescribedPredicate.describe("are not listed in " + resource, type -> !listed(type.getName(), known));
    }

    static boolean listed(String className, Set<String> known) {
        if (known.contains(className)) {
            return true;
        }
        int nested = className.indexOf('$');
        return nested > 0 && known.contains(className.substring(0, nested));
    }

    static ClassLoader classLoader() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        return context != null ? context : KnownViolations.class.getClassLoader();
    }
}
