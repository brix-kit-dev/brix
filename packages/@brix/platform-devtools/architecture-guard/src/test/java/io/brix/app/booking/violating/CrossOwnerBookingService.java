/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.app.booking.violating;

import io.brix.app.caseapp.repository.CaseRepository;

class CrossOwnerBookingService {

    private final CaseRepository repository;

    CrossOwnerBookingService(CaseRepository repository) {
        this.repository = repository;
    }
}
