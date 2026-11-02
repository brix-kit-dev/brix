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
package io.brix.samples.fixturealpha.server;

import io.brix.samples.fixturebeta.api.BetaStockQuery;

/**
 * A "fixturealpha" class that uses only the other plugin's published contract: allowed.
 */
public final class AlphaEndpoint {

    /**
     * Builds the query it would send.
     *
     * @param sku the item
     * @return the other plugin's contract type
     */
    public BetaStockQuery stockOf(String sku) {
        return new BetaStockQuery(sku);
    }
}
