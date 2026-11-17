/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.context;

/**
 * Stand-in for Spring's container, which an operational module must not reach (root A-26).
 */
public interface ApplicationContext {

    /**
     * Returns a bean.
     *
     * @param type the bean type
     * @param <T> the bean type
     * @return the bean
     */
    <T> T getBean(Class<T> type);
}
