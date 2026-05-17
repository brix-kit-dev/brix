/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.infra.adapter.outbox;

import io.runtime.sdk.event.CriticalEvent;
import io.runtime.sdk.event.IntegrationEvent;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * AOP aspect that transparently routes {@link CriticalEvent}-annotated integration
 * events through the {@link OutboxEventPublisher} for guaranteed, transactional delivery.
 *
 * <h3>Interception Rule</h3>
 * <p>Intercepts all calls to
 * {@link io.runtime.sdk.capability.EventBusCapability#publishIntegration(IntegrationEvent)}.
 * If the event class carries the {@link CriticalEvent} annotation with
 * {@code outbox = true}, the call is redirected to
 * {@link OutboxEventPublisher#saveForLater(IntegrationEvent)}.
 * For all other events, the original publish path proceeds unchanged.</p>
 *
 * <h3>Architecture Alignment</h3>
 * <p>This aspect resides in <em>Layer 2C — Infra Adapter</em>
 * ({@code infra-adapter-kafka-outbox}).  It bridges the contract-level annotation
 * ({@code @CriticalEvent} in {@code runtime-sdk-api}, Layer 2A) with the
 * infrastructure-level Outbox implementation.  Plugins remain decoupled:
 * they annotate events and call {@code publishIntegration()} as usual,
 * and the aspect handles the routing transparently (R13.4 compliance).</p>
 *
 * <h3>Thread Safety</h3>
 * <p>This class is stateless and thread-safe. All mutable state is managed
 * by {@link OutboxEventPublisher} which uses JPA transactions.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @see CriticalEvent
 * @see OutboxEventPublisher
 */
@Aspect
public class CriticalEventOutboxAspect {

    private static final Logger log = LoggerFactory.getLogger(CriticalEventOutboxAspect.class);

    private final OutboxEventPublisher outboxEventPublisher;

    /**
     * Creates a new {@code CriticalEventOutboxAspect}.
     *
     * @param outboxEventPublisher the Outbox publisher for guaranteed delivery;
     *                             must not be {@code null}
     */
    public CriticalEventOutboxAspect(OutboxEventPublisher outboxEventPublisher) {
        this.outboxEventPublisher = Objects.requireNonNull(outboxEventPublisher,
                "outboxEventPublisher must not be null");
    }

    /**
     * Intercepts {@code EventBusCapability.publishIntegration(IntegrationEvent)} calls
     * and routes {@link CriticalEvent}-annotated events through the Outbox pattern.
     *
     * <p>Decision flow:</p>
     * <ol>
     *   <li>Extract the first argument (expected to be an {@link IntegrationEvent})</li>
     *   <li>Check whether the event class is annotated with {@code @CriticalEvent(outbox = true)}</li>
     *   <li>If yes, delegate to {@link OutboxEventPublisher#saveForLater(IntegrationEvent)}
     *       and return without proceeding to the original Kafka publish path</li>
     *   <li>If no, let the original method proceed via {@code pjp.proceed()}</li>
     * </ol>
     *
     * @param pjp the join point representing the intercepted method call
     * @return the result of the original method, or {@code null} when redirected to Outbox
     * @throws Throwable if the original method throws
     */
    @Around("execution(* io.runtime.sdk.capability.EventBusCapability.publishIntegration(..))")
    public Object interceptPublishIntegration(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        if (args.length > 0 && args[0] instanceof IntegrationEvent) {
            IntegrationEvent event = (IntegrationEvent) args[0];
            CriticalEvent annotation = event.getClass().getAnnotation(CriticalEvent.class);

            if (annotation != null && annotation.outbox()) {
                log.info("Routing @CriticalEvent to Outbox: eventId={}, type={}",
                        event.getEventId(), event.getEventType());
                outboxEventPublisher.saveForLater(event);
                return null;
            }
        }
        return pjp.proceed();
    }
}
