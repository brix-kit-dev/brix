/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.page;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The paging convention (standard-link plan L7.8, D61).
 */
class PageRequestTest {

    @Test
    void absentParametersTakeTheFirstPageOfTheDefaultSize() {
        PageRequest request = PageRequest.of(null, null, null);

        assertEquals(1, request.page());
        assertEquals(PageRequest.DEFAULT_SIZE, request.size());
        assertEquals(0, request.offset());
        assertTrue(request.sort().isEmpty());
    }

    @Test
    void aSortCriterionNamesAPropertyAndADirection() {
        PageRequest request = PageRequest.of(3, 10, List.of("createdAt,desc", "name"));

        assertEquals(20, request.offset());
        assertEquals(new PageRequest.Sort("createdAt", true), request.sort().get(0));
        assertFalse(request.sort().get(1).descending());
        assertThrows(IllegalArgumentException.class, () -> PageRequest.of(1, 10, List.of("name,sideways")));
        assertThrows(IllegalArgumentException.class, () -> PageRequest.of(1, 10, List.of(",asc")));
    }

    @Test
    void aPageStartsAt1AndHoldsAtMost100Items() {
        assertThrows(IllegalArgumentException.class, () -> PageRequest.of(0, 10, null));
        assertThrows(IllegalArgumentException.class, () -> PageRequest.of(1, 101, null));
        assertThrows(IllegalArgumentException.class, () -> new Page<>(List.of("a", "b"), 1, 1, 2));
        Page<String> page = Page.of(PageRequest.of(2, 2, null), List.of("c", "d"), 5);
        assertEquals(List.of("c", "d"), page.items());
        assertEquals(5, page.total());
    }
}
