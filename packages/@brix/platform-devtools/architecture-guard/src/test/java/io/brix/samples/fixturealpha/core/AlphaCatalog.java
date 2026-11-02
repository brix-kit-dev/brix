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
package io.brix.samples.fixturealpha.core;

import io.brix.samples.fixturebeta.core.BetaStock;

/**
 * A "fixturealpha" class that uses another plugin's implementation class: forbidden (A-9).
 */
public final class AlphaCatalog {

    private final BetaStock stock;

    /**
     * Creates the violating fixture.
     *
     * @param stock the other plugin's implementation class
     */
    public AlphaCatalog(BetaStock stock) {
        this.stock = stock;
    }

    /**
     * Returns the forbidden dependency for bytecode import.
     *
     * @return stock
     */
    public BetaStock stock() {
        return stock;
    }
}
