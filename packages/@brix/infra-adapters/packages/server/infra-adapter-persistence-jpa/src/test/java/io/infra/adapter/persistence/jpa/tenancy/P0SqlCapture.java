/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa.tenancy;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/** Records the SQL Hibernate renders, so P0 can show the predicates a verifier would see. */
final class P0SqlCapture implements StatementInspector {

    static final P0SqlCapture INSTANCE = new P0SqlCapture();

    private static final long serialVersionUID = 1L;

    private final List<String> statements = new ArrayList<>();

    private P0SqlCapture() {
    }

    @Override
    public synchronized String inspect(String sql) {
        statements.add(sql);
        return sql;
    }

    synchronized void clear() {
        statements.clear();
    }

    synchronized List<String> statements() {
        return List.copyOf(statements);
    }
}
