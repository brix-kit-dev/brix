/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.brix.samples.orders.api.SampleOrderEvents;
import io.brix.samples.orders.api.SampleOrderQuantityAdjustedPayload;
import io.brix.samples.orders.internal.AdjustOrderQuantity;
import io.brix.samples.orders.internal.AdjustedOrder;
import io.brix.samples.orders.internal.SampleOrderAdministrationException;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.capability.UnitOfWork;
import io.runtime.sdk.event.DomainEvent;
import io.runtime.sdk.event.IntegrationEvent;

/**
 * The contract announces each change it makes, as the order is after it, and nothing it refuses (standard-link plan
 * D125). The runtime puts what it publishes into the plugin's outbox in the contract call's unit of work; here the
 * plugin's bus only records it.
 */
class OrderCorrectionsTest {

    private static final long TENANT = 42L;

    private final Map<Long, SampleOrder> stored = new HashMap<>();
    private final List<IntegrationEvent> published = new ArrayList<>();
    private final OrderCorrections corrections = new OrderCorrections(persistence(), bus());

    @Test
    void aChangeIsAnnouncedWithTheOrderAsItIsNow() {
        stored.put(1001L, SampleOrder.place(1001L, TENANT, "sku-1", 3, OffsetDateTime.of(2026, 11, 13, 9, 0, 0, 0,
            ZoneOffset.UTC)));

        AdjustedOrder adjusted = corrections.adjustQuantity(new AdjustOrderQuantity(TENANT, 1001L, 2));

        assertEquals(new AdjustedOrder("1001", 5, 2L), adjusted);
        assertEquals(1, published.size());
        IntegrationEvent event = published.get(0);
        assertEquals(SampleOrderEvents.QUANTITY_ADJUSTED, event.eventTypeId());
        assertEquals(new SampleOrderQuantityAdjustedPayload(TENANT, "1001", "sku-1", 5, 2L), event.payload());
        // One order's key, as SampleOrderPlaced has, and the order's tenant: a TENANT event.
        assertEquals("order.1001", event.getRoutingKey());
        assertEquals("42", event.getTenantId());
    }

    @Test
    void aRefusedChangeAnnouncesNothing() {
        stored.put(1001L, SampleOrder.place(1001L, TENANT, "sku-1", 3, OffsetDateTime.of(2026, 11, 13, 9, 0, 0, 0,
            ZoneOffset.UTC)));

        SampleOrderAdministrationException tooFew = assertThrows(SampleOrderAdministrationException.class,
            () -> corrections.adjustQuantity(new AdjustOrderQuantity(TENANT, 1001L, -3)));
        assertEquals(SampleOrderAdministrationException.QUANTITY_INVALID, tooFew.code());
        SampleOrderAdministrationException unknown = assertThrows(SampleOrderAdministrationException.class,
            () -> corrections.adjustQuantity(new AdjustOrderQuantity(TENANT, 1002L, 1)));
        assertEquals(SampleOrderAdministrationException.ORDER_NOT_FOUND, unknown.code());

        assertTrue(published.isEmpty(), "nothing announced: " + published);
    }

    private PersistenceCapability persistence() {
        SampleOrderRepository orders = (SampleOrderRepository) Proxy.newProxyInstance(
            SampleOrderRepository.class.getClassLoader(), new Class<?>[] {SampleOrderRepository.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "findById" -> Optional.ofNullable(stored.get((Long) arguments[0]));
                case "save" -> {
                    SampleOrder order = (SampleOrder) arguments[0];
                    stored.put(order.getId(), order);
                    yield order;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        return new PersistenceCapability() {
            @Override
            public <R> R repository(Class<R> repositoryType) {
                return repositoryType.cast(orders);
            }

            @Override
            public UnitOfWork unitOfWork() {
                throw new UnsupportedOperationException("the runtime opens the contract call's unit of work");
            }
        };
    }

    private EventBusCapability bus() {
        return new EventBusCapability() {
            @Override
            public void publish(DomainEvent event) {
                throw new UnsupportedOperationException("a contract announces integration events");
            }

            @Override
            public void publishIntegration(IntegrationEvent event) {
                published.add(event);
            }
        };
    }
}
