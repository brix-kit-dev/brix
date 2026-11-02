/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.samples.fixturebooking.domain;

import java.sql.Connection;

class DomainJdbcLeak {

    private final Connection connection;

    DomainJdbcLeak(Connection connection) {
        this.connection = connection;
    }
}
