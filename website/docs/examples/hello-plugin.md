---
id: hello-plugin
title: Hello Plugin Example
sidebar_label: Hello Plugin
sidebar_position: 1
---

# Hello Plugin Example

A minimal Brix plugin demonstrating core concepts.

## Project Structure

```
hello-plugin/
├── hello-plugin-api/                     # plugin-api: the response record and the JSON schemas
│   ├── src/main/java/com/example/hello/api/
│   │   └── Greeting.java
│   ├── src/main/resources/META-INF/brix/schemas/hello/
│   │   ├── greet-request/1.0.0.json
│   │   └── greeting/1.0.0.json
│   └── pom.xml
├── hello-plugin-core/                    # plugin-core: the domain service
│   ├── src/main/java/com/example/hello/core/
│   │   └── GreetingService.java
│   └── pom.xml
├── hello-plugin-server/                  # plugin-server: plugin class, handler, manifest
│   ├── src/main/java/com/example/hello/server/
│   │   ├── HelloPlugin.java
│   │   └── GreetHandler.java
│   ├── src/main/resources/META-INF/
│   │   ├── brix/plugin-manifest.yaml
│   │   └── services/io.runtime.sdk.plugin.BrixPlugin
│   └── pom.xml
├── hello-plugin-web/
│   ├── src/
│   │   ├── index.ts
│   │   ├── views/
│   │   │   └── HelloPage.tsx
│   │   └── viewmodels/
│   │       └── useGreeting.ts
│   ├── package.json
│   └── tsconfig.json
└── pom.xml
```

## Backend Implementation

The backend has no controller and no Spring configuration. The manifest declares `GET /api/greetings`, and the
plugin class binds the factory of its handler. The runtime checks the caller and binds the tenant before it calls
the handler.

### Response

```java
// hello-plugin-api/src/main/java/com/example/hello/api/Greeting.java
package com.example.hello.api;

/** The response of GET /api/greetings, as the greeting schema describes it. */
public record Greeting(String message, int totalCount) {
}
```

### Service

```java
// hello-plugin-core/src/main/java/com/example/hello/core/GreetingService.java
package com.example.hello.core;

import java.time.Duration;
import java.util.Objects;

import com.example.hello.api.Greeting;
import io.runtime.sdk.capability.StateStoreCapability;

/**
 * Greets, and counts each tenant's greetings for an hour. A plain class: the handler factory creates it with the
 * StateStoreCapability the manifest requires.
 */
public final class GreetingService {

    private static final Duration COUNT_TTL = Duration.ofHours(1);

    private final StateStoreCapability state;

    public GreetingService(StateStoreCapability state) {
        this.state = Objects.requireNonNull(state, "state must not be null");
    }

    /**
     * Returns a greeting and the tenant's greeting count, this one included.
     *
     * @param tenantId the tenant the runtime verified for the caller
     * @param name the name to greet
     * @return the greeting
     */
    public Greeting greet(String tenantId, String name) {
        // The key names the plugin and the tenant, so tenants never see each other's count.
        String key = "hello:greetings:" + tenantId;
        int count = state.get(key, Integer.class).orElse(0) + 1;
        state.put(key, count, COUNT_TTL);
        return new Greeting(String.format("Hello, %s! (Greeting #%d)", name, count), count);
    }
}
```

The read and the write are two calls, so two greetings at the same moment can share a number. That is acceptable
for a counter in an example.

### Endpoint Handler

```java
// hello-plugin-server/src/main/java/com/example/hello/server/GreetHandler.java
package com.example.hello.server;

import java.util.List;
import java.util.Objects;

import com.example.hello.api.Greeting;
import com.example.hello.core.GreetingService;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointHandlingException;
import io.runtime.sdk.plugin.EndpointInvocation;

/** GET /api/greetings?name=...: the returned record becomes a 200 response with a JSON body. */
final class GreetHandler implements EndpointHandler<EndpointInvocation<Object>, Greeting> {

    private final GreetingService greetings;

    GreetHandler(GreetingService greetings) {
        this.greetings = Objects.requireNonNull(greetings, "greetings must not be null");
    }

    @Override
    public Greeting handle(EndpointInvocation<Object> request) {
        List<String> names = request.queryParameters().getOrDefault("name", List.of());
        String name = names.isEmpty() || names.get(0).isBlank() ? "World" : names.get(0);
        if (name.length() > 100) {
            throw new EndpointHandlingException(400, "HELLO_NAME_TOO_LONG", "A name has at most 100 characters");
        }
        return greetings.greet(request.tenantId().orElseThrow(), name);
    }
}
```

### Plugin Class

```java
// hello-plugin-server/src/main/java/com/example/hello/server/HelloPlugin.java
package com.example.hello.server;

import com.example.hello.core.GreetingService;
import io.runtime.sdk.capability.StateStoreCapability;
import io.runtime.sdk.plugin.BrixHealth;
import io.runtime.sdk.plugin.BrixPlugin;
import io.runtime.sdk.plugin.PluginBootstrapContext;
import io.runtime.sdk.plugin.PluginContext;

public final class HelloPlugin implements BrixPlugin {

    /** The handler of GET /api/greetings, as the manifest names it. */
    static final String GREET = "hello.greet.v1";

    @Override
    public void configure(PluginBootstrapContext bootstrap) {
        // The runtime calls the factory with the plugin's context; StateStoreCapability is listed in the manifest.
        bootstrap.bindEndpointHandlerFactory(GREET,
            context -> new GreetHandler(new GreetingService(context.require(StateStoreCapability.class))));
    }

    @Override
    public void onStart(PluginContext context) {
    }

    @Override
    public void onStop() {
    }

    @Override
    public BrixHealth health() {
        return BrixHealth.up();
    }
}
```

`hello-plugin-server/src/main/resources/META-INF/services/io.runtime.sdk.plugin.BrixPlugin` holds one line:
`com.example.hello.server.HelloPlugin`.

### Manifest

```yaml
# hello-plugin-server/src/main/resources/META-INF/brix/plugin-manifest.yaml
apiVersion: brix.io/v1
kind: Plugin
metadata:
  pluginId: hello
  name: hello
  version: 1.0.0
  displayName: Hello
  vendor: Example Inc.
  license: Apache-2.0
runtime:
  compiledAgainst: 3.2.0
  supportedRange: ">=3.2.0 <4.0.0"
modules:
  - groupId: com.example
    artifactId: hello-plugin-api
    version: 1.0.0
    moduleKind: plugin-api
  - groupId: com.example
    artifactId: hello-plugin-core
    version: 1.0.0
    moduleKind: plugin-core
  - groupId: com.example
    artifactId: hello-plugin-server
    version: 1.0.0
    moduleKind: plugin-server
endpoints:
  provides:
    - endpointId: hello.greet.v1
      transport: http
      method: GET
      path: /api/greetings
      request:
        schemaRef: brix://schemas/hello/greet-request/1.0.0
      response:
        schemaRef: brix://schemas/hello/greeting/1.0.0
      handlerId: hello.greet.v1
      # A signed-in member of a tenant; the runtime binds the tenant from the token.
      authentication:
        mode: required
        policyRef: tenant-actor
      authorization:
        permissions:
          - hello:greeting:read
        decision: all
        resourcePolicyRef: hello-greet
      tenantContext:
        mode: required
        source: runtime
      timeout:
        policyRef: endpoint-default
      idempotency:
        mode: not-applicable
capabilities:
  required:
    - id: io.runtime.sdk.capability.StateStoreCapability
      version: ">=3.0.0 <4.0.0"
  optional: []
queries:
  provides: []
  consumes: []
commands:
  provides: []
  consumes: []
events:
  publishes: []
  subscribes: []
tasks:
  provides: []
internalContracts:
  provides: []
permissions:
  - id: hello:greeting:read
    name: Ask for a greeting
    description: Allows asking for a greeting in the current tenant.
    grants:
      - {role: OWNER, scope: TENANT}
      - {role: ADMIN, scope: TENANT}
      - {role: MEMBER, scope: TENANT}
config: []
```

The plugin keeps no tables, so the manifest has no `data` section. The host must provide a `StateStoreCapability`,
or the plugin does not start.

`greet-request/1.0.0.json` describes the `name` query parameter. `greeting/1.0.0.json` describes the response:

```json title="hello-plugin-api: META-INF/brix/schemas/hello/greeting/1.0.0.json"
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "brix://schemas/hello/greeting/1.0.0",
  "title": "A greeting",
  "type": "object",
  "additionalProperties": false,
  "required": ["message", "totalCount"],
  "properties": {
    "message": { "type": "string" },
    "totalCount": { "type": "integer", "minimum": 1 }
  }
}
```

### Maven POMs

The backend compiles against the SDK only:

```xml
<!-- hello-plugin-core/pom.xml -->
<dependencies>
    <dependency>
        <groupId>com.example</groupId>
        <artifactId>hello-plugin-api</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>io.brix</groupId>
        <artifactId>runtime-sdk-api</artifactId>
        <version>3.2.0</version>
    </dependency>
</dependencies>

<!-- hello-plugin-server/pom.xml -->
<dependencies>
    <dependency>
        <groupId>com.example</groupId>
        <artifactId>hello-plugin-core</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>io.brix</groupId>
        <artifactId>runtime-sdk-api</artifactId>
        <version>3.2.0</version>
    </dependency>
</dependencies>
```

`hello-plugin-api` has no dependencies. The backend needs no `spring-web` and no Spring configuration.

## Frontend Implementation

### Package Configuration

```json
// hello-plugin-web/package.json
{
  "name": "@example/hello-plugin-web",
  "version": "1.0.0",
  "type": "module",
  "main": "dist/index.js",
  "dependencies": {
    "@brix/runtime-sdk-api-web": "workspace:*",
    "@brix/shared-runtime-web": "workspace:*"
  }
}
```

### ViewModel Hook

```typescript
// hello-plugin-web/src/viewmodels/useGreeting.ts
import { useState, useCallback } from '@brix/shared-runtime-web';
import { useCapability, HttpCapability } from '@brix/runtime-sdk-api-web';

interface GreetingResponse {
  message: string;
  totalCount: number;
}

export function useGreeting() {
  const [greeting, setGreeting] = useState<string>('');
  const [count, setCount] = useState<number>(0);
  const [isLoading, setIsLoading] = useState(false);
  
  const http = useCapability(HttpCapability);
  
  const greet = useCallback(async (name: string) => {
    setIsLoading(true);
    try {
      const response = await http.get<GreetingResponse>(
        `/api/greetings?name=${encodeURIComponent(name)}`
      );
      setGreeting(response.message);
      setCount(response.totalCount);
    } finally {
      setIsLoading(false);
    }
  }, [http]);
  
  return { greeting, count, isLoading, greet };
}
```

### View Component

```tsx
// hello-plugin-web/src/views/HelloPage.tsx
import { React, useState } from '@brix/shared-runtime-web';
import { useGreeting } from '../viewmodels/useGreeting';

export function HelloPage() {
  const [name, setName] = useState('');
  const { greeting, count, isLoading, greet } = useGreeting();
  
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    greet(name || 'World');
  };
  
  return (
    <div className="hello-page">
      <h1>Hello Plugin</h1>
      
      <form onSubmit={handleSubmit}>
        <input
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Enter your name"
        />
        <button type="submit" disabled={isLoading}>
          {isLoading ? 'Loading...' : 'Greet'}
        </button>
      </form>
      
      {greeting && (
        <div className="greeting-result">
          <p className="message">{greeting}</p>
          <p className="count">Total greetings: {count}</p>
        </div>
      )}
    </div>
  );
}
```

### Module Entry

```typescript
// hello-plugin-web/src/index.ts
export { HelloPage } from './views/HelloPage';
export { useGreeting } from './viewmodels/useGreeting';
```

## Testing

### Backend Test

The service is a plain class, so the test creates it with a mocked capability. The test uses JUnit, AssertJ and
Mockito:

```java
// hello-plugin-core/src/test/java/com/example/hello/core/GreetingServiceTest.java
package com.example.hello.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.hello.api.Greeting;
import io.runtime.sdk.capability.StateStoreCapability;

class GreetingServiceTest {

    private final StateStoreCapability state = mock(StateStoreCapability.class);
    private final GreetingService service = new GreetingService(state);

    @Test
    void countsTheTenantsGreetings() {
        when(state.get("hello:greetings:42", Integer.class)).thenReturn(Optional.of(5));

        Greeting greeting = service.greet("42", "Alice");

        assertThat(greeting.message()).isEqualTo("Hello, Alice! (Greeting #6)");
        assertThat(greeting.totalCount()).isEqualTo(6);
        verify(state).put(eq("hello:greetings:42"), eq(6), any());
    }

    @Test
    void startsCountingAtOne() {
        when(state.get("hello:greetings:42", Integer.class)).thenReturn(Optional.empty());

        assertThat(service.greet("42", "Bob").message()).isEqualTo("Hello, Bob! (Greeting #1)");
    }
}
```

### Frontend Test

```typescript
// hello-plugin-web/src/viewmodels/__tests__/useGreeting.test.ts
import { renderHook, act, waitFor } from '@testing-library/react';
import { mockCapability } from '@brix/testing-utils-web';
import { HttpCapability } from '@brix/runtime-sdk-api-web';
import { useGreeting } from '../useGreeting';

describe('useGreeting', () => {
  it('should fetch greeting', async () => {
    const mockHttp = mockCapability(HttpCapability);
    mockHttp.get.mockResolvedValue({
      message: 'Hello, Test!',
      totalCount: 42,
    });
    
    const { result } = renderHook(() => useGreeting());
    
    await act(async () => {
      await result.current.greet('Test');
    });
    
    expect(result.current.greeting).toBe('Hello, Test!');
    expect(result.current.count).toBe(42);
  });
});
```

## Running the Example

A plugin runs inside a host. The host is a Spring Boot application with no business code. The plugin gets into it as
a Maven dependency, and the runtime finds `HelloPlugin` through its services file: the host needs no code for it.

1. Install the plugin's artifacts:

   ```bash
   mvn clean install
   ```

2. In the host's `pom.xml`, depend on `com.example:hello-plugin-server`. It brings `hello-plugin-core` and
   `hello-plugin-api`.
3. Give the host what the plugin needs:
   - the runtime, `io.brix:runtime-orchestrator`, and `spring-boot-starter-webmvc`;
   - a `StateStoreCapability`, the one capability the manifest requires: `io.brix:infra-adapter-redis` for Redis, or
     `io.brix:infra-adapter-simple` with `brix.infra.simple.enabled=true` for an in-memory store;
   - the identity provider your platform uses, because the endpoint requires a signed-in tenant member.
4. List the plugin as required, so that the host is not ready without it:

   ```properties
   brix.runtime-shell.required-plugins[0]=hello
   ```

5. Start the host, for example with `mvn spring-boot:run` in its directory.

`sample-e2e` in the repository is a host built this way, for two plugins on PostgreSQL and Kafka; see
[Running it end to end](./event-driven-plugin.md#running-it-end-to-end).

`GET /api/greetings?name=Developer` answers signed-in members of a tenant only. Open the Hello page in the web UI, or
send the request with a member's access token. The response is
`{"message":"Hello, Developer! (Greeting #1)","totalCount":1}`.

## Key Takeaways

1. **Services use capabilities** - `StateStoreCapability` from the handler factory instead of a Redis client
2. **Handlers are thin** - The manifest declares the endpoint; the handler reads the request and delegates
3. **Frontend uses hooks** - `useGreeting` encapsulates state
4. **Views are pure** - `HelloPage` only renders
5. **Easy to test** - Mock capabilities, not infrastructure

## Next Steps

- [CRUD Plugin Example](./crud-plugin) - Full CRUD operations
- [Event-Driven Plugin](./event-driven-plugin) - Event handling
