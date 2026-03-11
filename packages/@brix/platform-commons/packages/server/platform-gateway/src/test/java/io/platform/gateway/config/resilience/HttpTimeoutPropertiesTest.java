/*
 * Copyright 2026 Brix Platform Authors
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
package io.brix.platform.gateway.config.resilience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * HTTP Timeout Configuration Properties Test
 *
 * @author Brix Platform Authors
 */
class HttpTimeoutPropertiesTest {

    @Test
    void shouldHaveDefaultValues() {
        HttpTimeoutProperties properties = new HttpTimeoutProperties();
        
        assertTrue(properties.isEnabled());
        assertEquals(5000, properties.getConnectTimeoutMs());
        assertEquals(30000, properties.getResponseTimeoutMs());
        assertEquals(60000, properties.getGlobalTimeoutMs());
        assertEquals(30000, properties.getReadTimeoutMs());
        assertEquals(30000, properties.getWriteTimeoutMs());
    }

    @Test
    void shouldAllowCustomValues() {
        HttpTimeoutProperties properties = new HttpTimeoutProperties();
        
        properties.setEnabled(false);
        properties.setConnectTimeoutMs(10000);
        properties.setResponseTimeoutMs(60000);
        properties.setGlobalTimeoutMs(120000);
        
        assertFalse(properties.isEnabled());
        assertEquals(10000, properties.getConnectTimeoutMs());
        assertEquals(60000, properties.getResponseTimeoutMs());
        assertEquals(120000, properties.getGlobalTimeoutMs());
    }
}
