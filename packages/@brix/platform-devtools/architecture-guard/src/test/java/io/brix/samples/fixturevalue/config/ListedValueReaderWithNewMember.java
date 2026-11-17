/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.fixturevalue.config;

import org.springframework.beans.factory.annotation.Value;

/**
 * A listed class that gained a second @Value field and a method with an @Value parameter: the module lists
 * {@code region} only.
 */
public class ListedValueReaderWithNewMember {

    @Value("${fixture.region:eu}")
    private String region;

    @Value("${fixture.zone:a}")
    private String zone;

    /**
     * Returns where calls go.
     *
     * @return the region and the zone
     */
    public String where() {
        return region + zone;
    }

    /**
     * Returns the configured timeout.
     *
     * @param seconds the configured timeout
     * @return the timeout
     */
    public int timeout(@Value("${fixture.timeout:5}") int seconds) {
        return seconds;
    }
}
