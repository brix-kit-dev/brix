/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.fixturevalue.config;

import org.springframework.beans.factory.annotation.Value;

/**
 * Reads a setting with @Value; the module lists nothing of this class.
 */
public class UnlistedValueReader {

    private final int limit;

    /**
     * Creates the reader.
     *
     * @param limit the configured limit
     */
    public UnlistedValueReader(@Value("${fixture.limit:10}") int limit) {
        this.limit = limit;
    }

    /**
     * Returns the limit.
     *
     * @return the limit
     */
    public int limit() {
        return limit;
    }
}
