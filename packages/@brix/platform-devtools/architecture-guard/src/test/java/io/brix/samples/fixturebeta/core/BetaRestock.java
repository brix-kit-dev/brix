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
package io.brix.samples.fixturebeta.core;

import io.brix.samples.fixturealpha.core.AlphaCatalog;

/**
 * A "fixturebeta" class that uses "fixturealpha"'s implementation, closing a cycle between the two plugins.
 */
public final class BetaRestock {

    private final AlphaCatalog catalog;

    /**
     * Creates the fixture.
     *
     * @param catalog the other plugin's implementation class
     */
    public BetaRestock(AlphaCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * Returns the dependency for bytecode import.
     *
     * @return catalog
     */
    public AlphaCatalog catalog() {
        return catalog;
    }
}
