/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.fixturevalue.config;

import org.springframework.beans.factory.annotation.Value;

/**
 * Reads two settings with @Value, a field and a method parameter; the module lists both members.
 */
public class ListedValueReader {

    @Value("${fixture.region:eu}")
    private String region;

    /**
     * Returns where calls go.
     *
     * @param endpoint the configured endpoint
     * @return the region and the endpoint
     */
    public String endpoint(@Value("${fixture.endpoint:local}") String endpoint) {
        return region + ":" + endpoint;
    }
}
