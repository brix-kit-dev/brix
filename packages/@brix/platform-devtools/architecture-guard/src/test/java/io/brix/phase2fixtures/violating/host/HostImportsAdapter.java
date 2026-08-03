/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.phase2fixtures.violating.host;

import io.brix.infra.adapter.kafka.KafkaAdapter;

public final class HostImportsAdapter {

    private final KafkaAdapter adapter = new KafkaAdapter();

    public KafkaAdapter adapter() {
        return adapter;
    }
}
