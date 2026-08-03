/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.phase2fixtures.violating.core;

import org.apache.kafka.clients.producer.KafkaProducer;

public final class PluginCoreUsesKafka {

    private final KafkaProducer producer = new KafkaProducer();

    public KafkaProducer producer() {
        return producer;
    }
}
