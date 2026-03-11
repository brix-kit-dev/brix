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
├── hello-plugin-core/
│   ├── src/main/java/com/example/hello/
│   │   ├── service/
│   │   │   └── GreetingService.java
│   │   ├── controller/
│   │   │   └── GreetingController.java
│   │   └── config/
│   │       └── HelloModuleConfig.java
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

### Service

```java
// hello-plugin-core/src/main/java/com/example/hello/service/GreetingService.java
package com.example.hello.service;

import io.brix.runtime.sdk.api.CacheCapability;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class GreetingService {
    
    private static final String CACHE_KEY = "greeting:count";
    
    private final CacheCapability cache;
    
    public GreetingService(CacheCapability cache) {
        this.cache = cache;
    }
    
    /**
     * Returns a personalized greeting.
     *
     * @param name the name to greet
     * @return greeting message
     */
    public String greet(String name) {
        // Track greeting count in cache
        int count = cache.get(CACHE_KEY, Integer.class)
            .orElse(0) + 1;
        cache.set(CACHE_KEY, count, Duration.ofHours(1));
        
        return String.format("Hello, %s! (Greeting #%d)", name, count);
    }
    
    /**
     * Returns the total greeting count.
     */
    public int getGreetingCount() {
        return cache.get(CACHE_KEY, Integer.class).orElse(0);
    }
}
```

### Controller

```java
// hello-plugin-core/src/main/java/com/example/hello/controller/GreetingController.java
package com.example.hello.controller;

import com.example.hello.service.GreetingService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/greetings")
public class GreetingController {
    
    private final GreetingService greetingService;
    
    public GreetingController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }
    
    @GetMapping
    public GreetingResponse greet(@RequestParam(defaultValue = "World") String name) {
        String message = greetingService.greet(name);
        int count = greetingService.getGreetingCount();
        return new GreetingResponse(message, count);
    }
    
    public record GreetingResponse(String message, int totalCount) {}
}
```

### Configuration

```java
// hello-plugin-core/src/main/java/com/example/hello/config/HelloModuleConfig.java
package com.example.hello.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "com.example.hello")
public class HelloModuleConfig {}
```

### Maven POM

```xml
<!-- hello-plugin-core/pom.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>io.brix</groupId>
        <artifactId>brix-parent</artifactId>
        <version>3.0.0</version>
    </parent>
    
    <artifactId>hello-plugin-core</artifactId>
    
    <dependencies>
        <dependency>
            <groupId>io.brix</groupId>
            <artifactId>runtime-sdk-api</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-web</artifactId>
        </dependency>
    </dependencies>
</project>
```

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

```java
// hello-plugin-core/src/test/java/com/example/hello/service/GreetingServiceTest.java
package com.example.hello.service;

import io.brix.runtime.sdk.api.CacheCapability;
import io.brix.testing.BrixTest;
import io.brix.testing.MockCapability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@BrixTest
class GreetingServiceTest {
    
    @MockCapability
    private CacheCapability cache;
    
    private GreetingService service;
    
    @BeforeEach
    void setUp() {
        service = new GreetingService(cache);
    }
    
    @Test
    void shouldGreetWithName() {
        when(cache.get(anyString(), eq(Integer.class)))
            .thenReturn(Optional.of(5));
        
        String result = service.greet("Alice");
        
        assertThat(result).isEqualTo("Hello, Alice! (Greeting #6)");
        verify(cache).set(eq("greeting:count"), eq(6), any());
    }
    
    @Test
    void shouldStartCountAtOne() {
        when(cache.get(anyString(), eq(Integer.class)))
            .thenReturn(Optional.empty());
        
        String result = service.greet("Bob");
        
        assertThat(result).isEqualTo("Hello, Bob! (Greeting #1)");
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

```bash
# Build
mvn clean install

# Run
cd enterprise-host
mvn spring-boot:run

# Access
curl "http://localhost:8080/api/greetings?name=Developer"
# {"message":"Hello, Developer! (Greeting #1)","totalCount":1}
```

## Key Takeaways

1. **Services use capabilities** - `CacheCapability` instead of direct Redis
2. **Controllers are thin** - Just delegate to services
3. **Frontend uses hooks** - `useGreeting` encapsulates state
4. **Views are pure** - `HelloPage` only renders
5. **Easy to test** - Mock capabilities, not infrastructure

## Next Steps

- [CRUD Plugin Example](./crud-plugin) - Full CRUD operations
- [Event-Driven Plugin](./event-driven-plugin) - Event handling
