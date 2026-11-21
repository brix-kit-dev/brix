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
 * Marks a constants class the contract generator wrote: the {@link QueryType} of each query or the
 * {@link io.runtime.sdk.command.CommandType} of each command an api module publishes, such as
 * {@code SampleShippingQueries} or {@code SampleOrdersCommands}.
 *
 * <p>A plugin takes a query or command type from the owner's generated constants, never makes one (root A-22): the
 * guard's plugin profile refuses {@code QueryType.of}, {@code QueryType.optional} and {@code CommandType.of} in any
 * class without this mark. Only the generator puts it on a class; written by hand it is a deliberate bypass that
 * review sees. The guard reads it from the bytecode; the runtime never does, hence class retention.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
@Since("3.3.0")
public @interface GeneratedContractConstants {
}
