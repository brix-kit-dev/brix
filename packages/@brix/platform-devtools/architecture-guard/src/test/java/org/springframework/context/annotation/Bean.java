/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package org.springframework.context.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Stand-in for Spring's {@code @Bean}, under its real name: the guard does not depend on Spring. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Bean {
}
