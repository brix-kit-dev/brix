/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.app.booking.service;

import io.brix.app.booking.repository.BookingRepository;

class CompliantBookingService {

    private final BookingRepository repository;

    CompliantBookingService(BookingRepository repository) {
        this.repository = repository;
    }
}
