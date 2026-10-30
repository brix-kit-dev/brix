/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

/**
 * Tells what fails a delivery from what fails the process.
 *
 * <p>Whatever a handler throws fails its delivery, Errors included: a class that cannot load, a stack overflow, a
 * failed assertion. The unit of work rolls back and the transport retries or parks the event, while the thread that
 * delivers keeps going (Kafka design §14 K10). A {@link VirtualMachineError} other than {@link StackOverflowError},
 * such as running out of memory, is the process failing rather than the handler, so it is passed on and never charged
 * to the event as an attempt.</p>
 */
final class Failures {

    private Failures() {
    }

    /**
     * Throws the failure on when it is the process's, not the delivery's.
     *
     * @param failure what the delivery threw
     */
    static void rethrowFatal(Throwable failure) {
        if (failure instanceof VirtualMachineError fatal && !(failure instanceof StackOverflowError)) {
            throw fatal;
        }
    }
}
