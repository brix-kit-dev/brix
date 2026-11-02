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
package io.brix.platform.fixture;

import io.infra.adapter.kafka.KafkaAdapter;

/**
 * A platform-commons class that uses an infrastructure adapter: forbidden.
 */
public final class PlatformUsesAdapter {

    private final KafkaAdapter adapter = new KafkaAdapter();

    /**
     * Returns the forbidden dependency for bytecode import.
     *
     * @return adapter
     */
    public KafkaAdapter adapter() {
        return adapter;
    }
}
