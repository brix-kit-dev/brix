/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.fixtureevents.core;

import org.springframework.context.event.EventListener;

/**
 * Negative fixture: a plugin class that subscribes with Spring's {@code @EventListener}.
 */
public class ListensToSpringEvents {

    /**
     * Receives an event.
     *
     * @param event the event
     */
    @EventListener
    public void on(Object event) {
        // stand-in
    }
}
