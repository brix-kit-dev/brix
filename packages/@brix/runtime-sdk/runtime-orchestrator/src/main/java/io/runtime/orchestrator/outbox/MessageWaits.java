/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The messages one relay has seen refused, and since when (root BRX-RS-RESILIENCE-002; standard-link plan D91, D92).
 *
 * <p>The count lives in memory: a host that restarts starts it again, and each host counts what it tried itself. The
 * store goes round the backlog, so the oldest waits keep their place: giving it to a new one would keep every wait from
 * reaching its limit. A wait that finds no place free is not counted until it does.</p>
 *
 * <p>The relay that sends or parks a message forgets it, but another host's relay does not hear of that. A wait ends at
 * its limit, so a count twice as old as the limit is of a message that went elsewhere: it counts no more, and it gives
 * up its place when the places run out.</p>
 */
final class MessageWaits {

    private static final int PLACES = 10_000;

    private final Duration limit;
    private final int places;
    private final Map<String, Wait> waits = new HashMap<>();

    MessageWaits(Duration limit) {
        this(limit, PLACES);
    }

    MessageWaits(Duration limit, int places) {
        this.limit = Objects.requireNonNull(limit, "limit must not be null");
        this.places = places;
    }

    /**
     * Counts a refusal of the message.
     *
     * @param messageId the message
     * @param now when it was refused
     * @return its wait so far, this refusal included
     */
    Wait refused(String messageId, OffsetDateTime now) {
        OffsetDateTime outlasted = now.minus(limit.multipliedBy(2));
        Wait wait = waits.get(messageId);
        if (wait != null && !wait.since().isBefore(outlasted)) {
            Wait again = new Wait(wait.since(), wait.times() + 1);
            waits.put(messageId, again);
            return again;
        }
        if (waits.size() >= places) {
            waits.values().removeIf(kept -> kept.since().isBefore(outlasted));
        }
        Wait first = new Wait(now, 1);
        if (waits.size() < places) {
            waits.put(messageId, first);
        }
        return first;
    }

    /** Forgets a message that left or was parked: a later wait of it, after a replay, starts again. */
    void forget(String messageId) {
        waits.remove(messageId);
    }

    /**
     * Leaves a time out of every wait: nothing was tried then, so it counts against no message.
     *
     * @param pause how long nothing was tried
     */
    void leaveOut(Duration pause) {
        waits.replaceAll((messageId, wait) -> new Wait(wait.since().plus(pause), wait.times()));
    }

    /**
     * A message the receiving side could not take.
     *
     * @param since when this relay first saw it refused
     * @param times how often it has been refused
     */
    record Wait(OffsetDateTime since, int times) {
    }
}
