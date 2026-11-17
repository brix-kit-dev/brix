/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.operational;

/**
 * Stand-in for the L2B operational SPI's context: an operational module may depend on it.
 */
public interface OperationalContext {

    /**
     * Returns a declared internal contract.
     *
     * @param type the contract type
     * @param <T> the contract type
     * @return the contract
     */
    <T> T requireInternalContract(Class<T> type);
}
