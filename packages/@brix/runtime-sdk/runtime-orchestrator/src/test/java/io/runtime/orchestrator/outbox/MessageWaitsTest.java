/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

/** What a relay remembers of the messages it saw refused (standard-link plan D91, D92). */
class MessageWaitsTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-11-07T00:00:00Z");
    private static final Duration LIMIT = Duration.ofHours(1);

    private final MessageWaits waits = new MessageWaits(LIMIT, 2);

    @Test
    void aWaitCountsFromItsFirstRefusal() {
        waits.refused("m-a", T0);

        assertEquals(new MessageWaits.Wait(T0, 2), waits.refused("m-a", T0.plusMinutes(5)));
    }

    @Test
    void aMessageForgottenStartsAgain() {
        waits.refused("m-a", T0);
        waits.forget("m-a");

        assertEquals(new MessageWaits.Wait(T0.plusMinutes(5), 1), waits.refused("m-a", T0.plusMinutes(5)));
    }

    @Test
    void aTimeLeftOutCountsAgainstNoWaitAndTheWaitGoesOn() {
        waits.refused("m-a", T0);
        waits.leaveOut(Duration.ofMinutes(10));

        assertEquals(new MessageWaits.Wait(T0.plusMinutes(10), 2), waits.refused("m-a", T0.plusMinutes(15)));
    }

    @Test
    void aCountTwiceAsOldAsTheLimitStartsAgain() {
        // A wait ends at its limit: its message went elsewhere, and this refusal is of a later wait.
        waits.refused("m-a", T0);
        OffsetDateTime later = T0.plus(LIMIT.multipliedBy(2)).plusSeconds(1);

        assertEquals(new MessageWaits.Wait(later, 1), waits.refused("m-a", later));
    }

    @Test
    void aCountAsOldAsTwiceTheLimitStillCounts() {
        waits.refused("m-a", T0);

        assertEquals(new MessageWaits.Wait(T0, 2), waits.refused("m-a", T0.plus(LIMIT.multipliedBy(2))));
    }

    @Test
    void whenThePlacesRunOutTheOldestWaitsStaySoThatTheyReachTheirLimit() {
        // The store goes round the backlog: with the oldest pushed out, no wait would ever end.
        waits.refused("m-a", T0);
        waits.refused("m-b", T0);

        assertEquals(new MessageWaits.Wait(T0.plusMinutes(1), 1), waits.refused("m-c", T0.plusMinutes(1)));
        assertEquals(new MessageWaits.Wait(T0.plusMinutes(2), 1), waits.refused("m-c", T0.plusMinutes(2)), "no place for it");
        assertEquals(new MessageWaits.Wait(T0, 2), waits.refused("m-a", T0.plusMinutes(3)));
    }

    @Test
    void aWaitThatFindsAPlaceFreeAgainIsCounted() {
        waits.refused("m-a", T0);
        waits.refused("m-b", T0);
        waits.refused("m-c", T0.plusMinutes(1));
        waits.forget("m-a");

        waits.refused("m-c", T0.plusMinutes(2));

        assertEquals(new MessageWaits.Wait(T0.plusMinutes(2), 2), waits.refused("m-c", T0.plusMinutes(3)));
    }

    @Test
    void aCountTwiceAsOldAsTheLimitGivesUpItsPlaceWhenThePlacesRunOut() {
        // Its message went elsewhere: only the relay that sent it forgets it.
        waits.refused("m-a", T0);
        waits.refused("m-b", T0.plusHours(1));
        OffsetDateTime later = T0.plus(LIMIT.multipliedBy(2)).plusSeconds(1);

        waits.refused("m-c", later);

        assertEquals(new MessageWaits.Wait(later, 2), waits.refused("m-c", later));
        assertEquals(new MessageWaits.Wait(T0.plusHours(1), 2), waits.refused("m-b", later), "this one is younger");
    }

    @Test
    void aCountWithinTwiceTheLimitKeepsItsPlace() {
        waits.refused("m-a", T0);
        waits.refused("m-b", T0);

        waits.refused("m-c", T0.plus(LIMIT.multipliedBy(2)));

        assertEquals(new MessageWaits.Wait(T0, 2), waits.refused("m-a", T0.plus(LIMIT.multipliedBy(2))));
    }
}
