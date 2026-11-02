/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.samples.fixturebooking.service;

import io.brix.samples.fixturebooking.repository.BookingRepository;

class CompliantBookingService {

    private final BookingRepository repository;

    CompliantBookingService(BookingRepository repository) {
        this.repository = repository;
    }
}
