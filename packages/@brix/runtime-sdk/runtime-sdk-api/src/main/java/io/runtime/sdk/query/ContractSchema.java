/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.query;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.runtime.sdk.annotation.Since;

/**
 * The versioned schema a generated contract type was generated from.
 *
 * <p>The contract generator puts it on every top-level request and result type. The runtime compares it with the
 * {@code schemaRef}s a plugin's manifest declares, so a provider and its callers are bound to the same contract
 * version without any second declaration (root BRX-RS-MANIFEST-003, A-22).</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Since("3.3.0")
public @interface ContractSchema {

    /**
     * Returns the schema's id.
     *
     * @return a {@code brix://schemas/<plugin>/<name>/<version>} reference
     */
    String value();
}
