/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.shipping.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import io.brix.samples.shipping.api.Shipment;
import io.runtime.sdk.capability.IdGeneratorCapability;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.capability.UnitOfWork;

/**
 * A shipment follows the newest state of its order, whichever event brings it and in whatever order they arrive:
 * events of different types are not ordered, and a parked event comes back after later ones (events guide, "What the
 * platform guarantees"). The order's version decides.
 */
class ShipmentDeskTest {

    private static final long TENANT = 42L;

    private final Map<String, SampleShipment> stored = new HashMap<>();
    private final AtomicInteger saves = new AtomicInteger();
    private final AtomicInteger ids = new AtomicInteger(7000);
    private final ShipmentDesk desk = new ShipmentDesk(persistence(), idGenerator(),
        Clock.fixed(Instant.parse("2026-11-13T09:00:00Z"), ZoneOffset.UTC));

    @Test
    void aChangeAfterThePlacementTakesTheNewQuantity() {
        desk.prepare(new OrderPlaced(TENANT, "1001", "sku-1", 3, 1L));
        desk.adjust(new OrderQuantityAdjusted(TENANT, "1001", "sku-1", 5, 2L));

        assertEquals(Optional.of(new Shipment("1001", "sku-1", 5, 2L)), desk.find(TENANT, "1001"));
    }

    @Test
    void aChangeThatArrivesBeforeThePlacementPreparesTheShipment() {
        desk.adjust(new OrderQuantityAdjusted(TENANT, "1001", "sku-1", 5, 2L));
        assertEquals(Optional.of(new Shipment("1001", "sku-1", 5, 2L)), desk.find(TENANT, "1001"));

        // The placement is older than what the shipment shows: it changes nothing and makes no second shipment.
        desk.prepare(new OrderPlaced(TENANT, "1001", "sku-1", 3, 1L));

        assertEquals(Optional.of(new Shipment("1001", "sku-1", 5, 2L)), desk.find(TENANT, "1001"));
        assertEquals(1, stored.size());
        assertEquals(7001, ids.get(), "one id, for the one shipment");
    }

    @Test
    void aChangeTheShipmentShowsOrThatALaterOneOvertookChangesNothing() {
        desk.prepare(new OrderPlaced(TENANT, "1001", "sku-1", 3, 1L));
        desk.adjust(new OrderQuantityAdjusted(TENANT, "1001", "sku-1", 7, 3L));

        // Version 2, replayed after it was parked, arrives after version 3.
        desk.adjust(new OrderQuantityAdjusted(TENANT, "1001", "sku-1", 5, 2L));
        // Version 3 again.
        desk.adjust(new OrderQuantityAdjusted(TENANT, "1001", "sku-1", 7, 3L));

        assertEquals(Optional.of(new Shipment("1001", "sku-1", 7, 3L)), desk.find(TENANT, "1001"));
        assertEquals(2, saves.get(), "saved when prepared and when version 3 arrived, not after");
    }

    @Test
    void eachOrderHasItsOwnShipment() {
        desk.prepare(new OrderPlaced(TENANT, "1001", "sku-1", 3, 1L));
        desk.adjust(new OrderQuantityAdjusted(TENANT, "1002", "sku-2", 4, 2L));
        desk.adjust(new OrderQuantityAdjusted(TENANT + 1, "1001", "sku-9", 9, 5L));

        assertEquals(Optional.of(new Shipment("1001", "sku-1", 3, 1L)), desk.find(TENANT, "1001"));
        assertEquals(Optional.of(new Shipment("1002", "sku-2", 4, 2L)), desk.find(TENANT, "1002"));
        assertEquals(Optional.of(new Shipment("1001", "sku-9", 9, 5L)), desk.find(TENANT + 1, "1001"));
    }

    private PersistenceCapability persistence() {
        SampleShipmentRepository shipments = (SampleShipmentRepository) Proxy.newProxyInstance(
            SampleShipmentRepository.class.getClassLoader(), new Class<?>[] {SampleShipmentRepository.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "findByTenantIdAndOrderId" -> Optional.ofNullable(stored.get(arguments[0] + "/" + arguments[1]));
                case "save" -> {
                    SampleShipment shipment = (SampleShipment) arguments[0];
                    stored.put(shipment.tenantId() + "/" + shipment.orderId(), shipment);
                    saves.incrementAndGet();
                    yield shipment;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        UnitOfWork unitOfWork = new UnitOfWork() {
            @Override
            public <T> T required(Supplier<T> work) {
                return work.get();
            }

            @Override
            public <T> T readOnly(Supplier<T> work) {
                return work.get();
            }
        };
        return new PersistenceCapability() {
            @Override
            public <R> R repository(Class<R> repositoryType) {
                return repositoryType.cast(shipments);
            }

            @Override
            public UnitOfWork unitOfWork() {
                return unitOfWork;
            }
        };
    }

    private IdGeneratorCapability idGenerator() {
        return new IdGeneratorCapability() {
            @Override
            public String generate() {
                return String.valueOf(ids.incrementAndGet());
            }

            @Override
            public String generate(String prefix) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<String> generateBatch(int count) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<String> generateBatch(String prefix, int count) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
