/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.fixturevalue.config;

import org.springframework.beans.factory.annotation.Value;

/**
 * The module's list names this class without a member, which lists nothing.
 */
public class ClassOnlyListedValueReader {

    @Value("${fixture.region:eu}")
    private String region;

    /**
     * Returns the region.
     *
     * @return the region
     */
    public String region() {
        return region;
    }
}
