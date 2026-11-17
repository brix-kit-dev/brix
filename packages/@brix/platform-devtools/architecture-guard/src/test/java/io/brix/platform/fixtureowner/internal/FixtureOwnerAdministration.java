/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.fixtureowner.internal;

/**
 * A Data Owner's internal contract: an operational module may use it.
 */
public interface FixtureOwnerAdministration {

    /**
     * Archives a record of the owner.
     *
     * @param id the record
     */
    void archive(long id);
}
