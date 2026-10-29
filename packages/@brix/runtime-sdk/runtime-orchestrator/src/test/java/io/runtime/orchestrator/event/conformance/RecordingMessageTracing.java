/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event.conformance;

import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.tracing.MessageTracing;

/**
 * Records the message spans the runtime starts, as a tracing adapter would: each span continues the trace of the
 * context its message carries, or starts one, under a span id of its own.
 */
public final class RecordingMessageTracing implements MessageTracing {

    /** The kind of a PRODUCER span. */
    public static final String PRODUCER = "PRODUCER";
    /** The kind of a CONSUMER span. */
    public static final String CONSUMER = "CONSUMER";

    private static final Pattern TRACEPARENT = Pattern.compile("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");

    /**
     * A span that has ended.
     *
     * @param kind {@link #PRODUCER} or {@link #CONSUMER}
     * @param eventId the message's event id
     * @param parent the trace context the message carried
     * @param traceparent the span's own trace context
     * @param subscription the subscription of a CONSUMER span
     * @param failed whether the span was marked failed
     */
    public record Recorded(String kind, String eventId, String parent, String traceparent, String subscription,
            boolean failed) {
    }

    private final List<Recorded> ended = new CopyOnWriteArrayList<>();

    @Override
    public MessageSpan producer(CanonicalOutboxMessage message) {
        return start(PRODUCER, message, null);
    }

    @Override
    public MessageSpan consumer(CanonicalOutboxMessage message, String subscription) {
        return start(CONSUMER, message, subscription);
    }

    /**
     * Returns the spans of one kind that have ended, in the order they ended.
     *
     * @param kind {@link #PRODUCER} or {@link #CONSUMER}
     * @return the spans
     */
    public List<Recorded> spans(String kind) {
        return ended.stream().filter(span -> span.kind().equals(kind)).toList();
    }

    private MessageSpan start(String kind, CanonicalOutboxMessage message, String subscription) {
        String parent = message.traceparent();
        String traceId = parent != null && TRACEPARENT.matcher(parent).matches() ? parent.substring(3, 35) : hex(16);
        String traceparent = "00-" + traceId + "-" + hex(8) + "-01";
        return new MessageSpan() {
            private boolean failed;

            @Override
            public String traceparent() {
                return traceparent;
            }

            @Override
            public String tracestate() {
                return message.tracestate();
            }

            @Override
            public void failed(Throwable failure) {
                failed = true;
            }

            @Override
            public void close() {
                ended.add(new Recorded(kind, message.eventId(), parent, traceparent, subscription, failed));
            }
        };
    }

    private static String hex(int bytes) {
        byte[] random = new byte[bytes];
        ThreadLocalRandom.current().nextBytes(random);
        random[0] |= 1;
        return HexFormat.of().formatHex(random);
    }
}
