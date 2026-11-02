---
id: create-first-plugin
title: Create Your First Plugin
sidebar_label: Create First Plugin
sidebar_position: 4
---

# Create Your First Plugin

This tutorial walks you through creating a complete TODO plugin with frontend, backend, and events.

## What We'll Build

A TODO management plugin that:
- Displays a list of TODOs (Web frontend)
- Creates new TODOs via REST API (Backend)
- Publishes events when TODOs are completed (Event-driven)
- Passes all Architecture Guard tests

## Part 1: Scaffold the Plugin

The scaffold CLI is `create-brix`, from the `@brix-sdk/create-brix` package
(`packages/@brix/platform-devtools/@brix/create-brix` in the repository). Its `plugin`, `service`, `app` and
`add-module` commands are legacy and fail. Generate a plugin with `phase7 plugin`:

```bash
create-brix phase7 plugin todo --runtime-version 3.2.0 --runtime-range ">=3.2.0 <4.0.0"
```

Pass the runtime version and range explicitly, so the plugin targets the runtime you run. In a checkout of the
repository, after `pnpm install`, `pnpm --filter @brix-sdk/create-brix build` builds the CLI and
`node packages/@brix/platform-devtools/@brix/create-brix/dist/cli.js` runs it.

The command generates the three backend modules and the plugin's entry point:

```text
app-todo/
├── pom.xml                                       parent of the three modules
├── app-todo-api/pom.xml                          plugin-api
├── app-todo-core/pom.xml                         plugin-core
├── app-todo-server/                              plugin-server
│   ├── pom.xml                                   depends on io.brix:runtime-sdk-api
│   └── src/main/
│       ├── java/io/brix/app/todo/server/TodoPlugin.java
│       └── resources/META-INF/
│           ├── brix/plugin-manifest.yaml
│           └── services/io.runtime.sdk.plugin.BrixPlugin
├── migration/phase7-migration-plan.yaml
└── README.md
```

The scaffold names the plugin `app-todo`. This tutorial uses shorter names. Either set works, as long as you use one
set throughout:

| | This tutorial | The scaffold |
|---|---|---|
| Backend modules | `todo-api`, `todo-core`, `todo-server`, in `backend/api`, `backend/core`, `backend/server` | `app-todo-api`, `app-todo-core`, `app-todo-server` |
| Plugin id | `todo` | `app-todo` |
| Java package | `com.example.todo` | `io.brix.app.todo` |
| Maven groupId | `com.example` | `io.brix.app` |

The manifest's `metadata.pluginId` and its `modules` must name your artifacts. Part 3.4 gives the complete
manifest; it replaces the generated one.

## Part 2: Define Shared Types

First, define the contract between frontend and backend:

```typescript title="frontend/shared/types/Todo.ts"
/**
 * TODO entity - shared between frontend and backend.
 * This module is Layer 1 (Plugin) shared contract.
 */
export interface Todo {
  id: string;
  title: string;
  completed: boolean;
  createdAt: string;
}

export interface CreateTodoRequest {
  title: string;
}

export interface TodoCompletedEvent {
  todoId: string;
  completedAt: string;
}
```

## Part 3: Implement Backend

The backend is three Maven modules: `backend/api` (the types other code reads, and the JSON schemas),
`backend/core` (entity, repository, service) and `backend/server` (handlers, plugin class, manifest). It has no
controllers and no Spring configuration. The manifest declares the endpoints and the event, the plugin class binds
a factory to each handler, and the runtime calls them. See the
[Backend Development Guide](../guides/backend-development.md) for each piece in detail.

### 3.1 Data Model

The TODOs live in the plugin's own table. The id comes from `IdGeneratorCapability`, and every row names its
tenant:

```java title="backend/core/model/Todo.java"
package com.example.todo.core.model;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "todo_item")
public class Todo implements Persistable<Long> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Transient
    private boolean fresh = true;

    protected Todo() {
    }

    public static Todo create(long id, long tenantId, String title, OffsetDateTime createdAt) {
        Todo todo = new Todo();
        todo.id = id;
        todo.tenantId = tenantId;
        todo.title = title;
        todo.createdAt = createdAt;
        return todo;
    }

    /** Marks the TODO completed; false when it already was. */
    public boolean complete() {
        if (completed) {
            return false;
        }
        completed = true;
        return true;
    }

    @PostPersist
    @PostLoad
    void stored() {
        fresh = false;
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return fresh;
    }

    // Getters for tenantId, title, completed and createdAt
}
```

```java title="backend/core/repository/TodoRepository.java"
package com.example.todo.core.repository;

import java.util.List;

import com.example.todo.core.model.Todo;
import org.springframework.data.jpa.repository.JpaRepository;

/** The persistence adapter limits every query to the caller's tenant. */
public interface TodoRepository extends JpaRepository<Todo, Long> {

    List<Todo> findAllByOrderByCreatedAtAsc();
}
```

```sql title="backend/server/resources/db/migration/todo/V1__todo.sql"
CREATE TABLE todo_item (
    id          BIGINT        NOT NULL,
    tenant_id   BIGINT        NOT NULL,
    title       VARCHAR(200)  NOT NULL,
    completed   BOOLEAN       NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_todo_item PRIMARY KEY (id)
);

CREATE INDEX idx_todo_item_tenant ON todo_item (tenant_id, created_at);
```

The migration creates only the business table. The platform creates the plugin's outbox, which carries the
`TodoCompleted` event.

### 3.2 Domain Service

```java title="backend/core/service/TodoService.java"
package com.example.todo.core.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.example.todo.core.model.Todo;
import com.example.todo.core.model.TodoCompleted;
import com.example.todo.core.repository.TodoRepository;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.IdGeneratorCapability;

/**
 * TODO domain service: a plain class.
 *
 * <p>A handler factory creates it with the repository and the capabilities it uses. It has no Spring
 * annotations, opens no transactions and imports no Kafka or Redis client: each call runs in the unit of
 * work the runtime opened for the handler, in the caller's tenant.</p>
 */
public final class TodoService {

    private final TodoRepository todos;
    private final EventBusCapability events;
    private final IdGeneratorCapability ids;
    private final Clock clock;

    public TodoService(TodoRepository todos, EventBusCapability events, IdGeneratorCapability ids, Clock clock) {
        this.todos = Objects.requireNonNull(todos, "todos must not be null");
        this.events = Objects.requireNonNull(events, "events must not be null");
        this.ids = Objects.requireNonNull(ids, "ids must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public Todo create(long tenantId, String title) {
        return todos.save(Todo.create(Long.parseLong(ids.generate()), tenantId, title, OffsetDateTime.now(clock)));
    }

    public List<Todo> list() {
        return todos.findAllByOrderByCreatedAtAsc();
    }

    /**
     * Completes a TODO. The change and the TodoCompleted event commit together: the event goes to the
     * plugin's outbox in the same transaction. Empty when there is no such TODO.
     */
    public Optional<Todo> complete(long todoId) {
        Optional<Todo> todo = todos.findById(todoId);
        todo.ifPresent(found -> {
            if (found.complete()) {
                events.publishIntegration(new TodoCompleted(found, OffsetDateTime.now(clock)));
            }
        });
        return todo;
    }
}
```

The event is an `IntegrationEvent` whose type and payload the manifest declares:

```java title="backend/core/model/TodoCompleted.java"
package com.example.todo.core.model;

import java.time.OffsetDateTime;

import io.runtime.sdk.event.IntegrationEvent;

public final class TodoCompleted extends IntegrationEvent {

    public static final String TYPE = "TodoCompleted";

    private static final long serialVersionUID = 1L;

    private final Payload payload;

    public TodoCompleted(Todo todo, OffsetDateTime completedAt) {
        // The plugin id, and the tenant: this makes it a TENANT event.
        super("todo", String.valueOf(todo.getTenantId()));
        this.payload = new Payload(todo.getTenantId(), String.valueOf(todo.getId()), completedAt.toString());
    }

    @Override
    public String eventTypeId() {
        return TYPE;                                  // events.publishes[].eventType
    }

    @Override
    public String getRoutingKey() {
        return "todo." + payload.todoId();            // events of one TODO stay in order
    }

    @Override
    public Object payload() {
        return payload;                               // what subscribers receive
    }

    /** The fields of the shared TodoCompletedEvent type, plus the tenant. */
    public record Payload(long tenantId, String todoId, String completedAt) {
    }
}
```

### 3.3 Endpoint Handlers

Each endpoint the manifest declares gets a small handler class. A handler reads the request from the
`EndpointInvocation`, calls the service and returns the result:

```java title="backend/server/TodoEndpoints.java"
package com.example.todo.server;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.example.todo.api.TodoView;
import com.example.todo.core.model.Todo;
import com.example.todo.core.service.TodoService;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointHandlingException;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.EndpointResponse;

final class TodoEndpoints {

    private TodoEndpoints() {
    }

    /** GET /api/todos: the tenant's TODOs, in a read-only unit of work. */
    static final class ListTodos implements EndpointHandler<EndpointInvocation<Object>, List<TodoView>> {

        private final TodoService todos;

        ListTodos(TodoService todos) {
            this.todos = Objects.requireNonNull(todos, "todos must not be null");
        }

        @Override
        public List<TodoView> handle(EndpointInvocation<Object> request) {
            return todos.list().stream().map(TodoEndpoints::view).toList();
        }
    }

    /** POST /api/todos with {"title": ...}: 201 with the new TODO. */
    static final class CreateTodo implements EndpointHandler<EndpointInvocation<Object>, EndpointResponse> {

        private final TodoService todos;

        CreateTodo(TodoService todos) {
            this.todos = Objects.requireNonNull(todos, "todos must not be null");
        }

        @Override
        public EndpointResponse handle(EndpointInvocation<Object> request) {
            Object title = request.body() instanceof Map<?, ?> body ? body.get("title") : null;
            if (!(title instanceof String text) || text.isBlank() || text.length() > 200) {
                throw new EndpointHandlingException(400, "TODO_INVALID",
                    "A TODO needs a title of at most 200 characters");
            }
            long tenantId = Long.parseLong(request.tenantId().orElseThrow());
            return EndpointResponse.created(view(todos.create(tenantId, text)));
        }
    }

    /** PATCH /api/todos/{id}/complete: 204, or 404 when there is no such TODO. */
    static final class CompleteTodo implements EndpointHandler<EndpointInvocation<Object>, EndpointResponse> {

        private final TodoService todos;

        CompleteTodo(TodoService todos) {
            this.todos = Objects.requireNonNull(todos, "todos must not be null");
        }

        @Override
        public EndpointResponse handle(EndpointInvocation<Object> request) {
            long id;
            try {
                id = Long.parseLong(request.pathVariables().get("id"));
            } catch (NumberFormatException notAnId) {
                throw notFound();
            }
            todos.complete(id).orElseThrow(CompleteTodo::notFound);
            return new EndpointResponse(204, null);
        }

        private static EndpointHandlingException notFound() {
            return new EndpointHandlingException(404, "TODO_NOT_FOUND", "No such TODO");
        }
    }

    /** The shared Todo type: id, title, completed, createdAt. */
    private static TodoView view(Todo todo) {
        return new TodoView(String.valueOf(todo.getId()), todo.getTitle(), todo.isCompleted(),
            todo.getCreatedAt().toString());
    }
}
```

`TodoView` is a record in `backend/api`: `record TodoView(String id, String title, boolean completed, String createdAt)`.

The plugin class binds a factory to each handler ID. The runtime calls the factories with the plugin's
`PluginContext`, the only way to reach a capability:

```java title="backend/server/TodoPlugin.java"
package com.example.todo.server;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;

import com.example.todo.core.repository.TodoRepository;
import com.example.todo.core.service.TodoService;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.IdGeneratorCapability;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.plugin.BrixHealth;
import io.runtime.sdk.plugin.BrixPlugin;
import io.runtime.sdk.plugin.PluginBootstrapContext;
import io.runtime.sdk.plugin.PluginContext;

public final class TodoPlugin implements BrixPlugin {

    private final AtomicReference<BrixHealth> health = new AtomicReference<>(BrixHealth.down("not started"));

    @Override
    public void configure(PluginBootstrapContext bootstrap) {
        bootstrap.bindEndpointHandlerFactory("todo.list.v1", context -> new TodoEndpoints.ListTodos(todos(context)));
        bootstrap.bindEndpointHandlerFactory("todo.create.v1",
            context -> new TodoEndpoints.CreateTodo(todos(context)));
        bootstrap.bindEndpointHandlerFactory("todo.complete.v1",
            context -> new TodoEndpoints.CompleteTodo(todos(context)));
    }

    private static TodoService todos(PluginContext context) {
        return new TodoService(
            context.require(PersistenceCapability.class).repository(TodoRepository.class),
            context.require(EventBusCapability.class),
            context.require(IdGeneratorCapability.class),
            Clock.systemUTC());
    }

    @Override
    public void onStart(PluginContext context) {
        health.set(BrixHealth.up());
    }

    @Override
    public void onStop() {
        health.set(BrixHealth.down("stopped"));
    }

    @Override
    public BrixHealth health() {
        return health.get();
    }
}
```

`backend/server/resources/META-INF/services/io.runtime.sdk.plugin.BrixPlugin` holds one line:
`com.example.todo.server.TodoPlugin`.

### 3.4 Plugin Manifest

The manifest declares the three endpoints, who may call them, the event, the capabilities and the data the plugin
owns:

```yaml title="backend/server/resources/META-INF/brix/plugin-manifest.yaml"
apiVersion: brix.io/v1
kind: Plugin
metadata:
  pluginId: todo
  name: todo
  version: 1.0.0
  displayName: TODO
  vendor: Example Inc.
  license: Apache-2.0
runtime:
  compiledAgainst: 3.2.0
  supportedRange: ">=3.2.0 <4.0.0"
modules:
  - groupId: com.example
    artifactId: todo-api
    version: 1.0.0
    moduleKind: plugin-api
  - groupId: com.example
    artifactId: todo-core
    version: 1.0.0
    moduleKind: plugin-core
  - groupId: com.example
    artifactId: todo-server
    version: 1.0.0
    moduleKind: plugin-server
endpoints:
  provides:
    - endpointId: todo.list.v1
      transport: http
      method: GET
      path: /api/todos
      request:
        schemaRef: brix://schemas/todo/list-todos-request/1.0.0
      response:
        schemaRef: brix://schemas/todo/todo-list/1.0.0
      handlerId: todo.list.v1
      authentication:
        mode: required
        policyRef: tenant-actor
      authorization:
        permissions:
          - todo:read
        decision: all
        resourcePolicyRef: todo-read
      tenantContext:
        mode: required
        source: runtime
      timeout:
        policyRef: endpoint-default
      idempotency:
        mode: not-applicable
    - endpointId: todo.create.v1
      transport: http
      method: POST
      path: /api/todos
      request:
        schemaRef: brix://schemas/todo/create-todo-request/1.0.0
      response:
        schemaRef: brix://schemas/todo/todo/1.0.0
      handlerId: todo.create.v1
      authentication:
        mode: required
        policyRef: tenant-actor
      authorization:
        permissions:
          - todo:write
        decision: all
        resourcePolicyRef: todo-write
      tenantContext:
        mode: required
        source: runtime
      timeout:
        policyRef: endpoint-default
      idempotency:
        mode: not-applicable
    - endpointId: todo.complete.v1
      transport: http
      method: PATCH
      path: /api/todos/{id}/complete
      request:
        schemaRef: brix://schemas/todo/complete-todo-request/1.0.0
      response:
        schemaRef: brix://schemas/todo/no-content/1.0.0
      handlerId: todo.complete.v1
      authentication:
        mode: required
        policyRef: tenant-actor
      authorization:
        permissions:
          - todo:write
        decision: all
        resourcePolicyRef: todo-write
      tenantContext:
        mode: required
        source: runtime
      timeout:
        policyRef: endpoint-default
      # Completing twice changes nothing and publishes the event once.
      idempotency:
        mode: not-applicable
capabilities:
  required:
    - id: io.runtime.sdk.capability.EventBusCapability
      version: ">=3.0.10 <4.0.0"
    - id: io.runtime.sdk.capability.PersistenceCapability
      version: ">=3.2.0 <4.0.0"
    - id: io.runtime.sdk.capability.IdGeneratorCapability
      version: ">=3.2.0 <4.0.0"
  optional: []
queries:
  provides: []
  consumes: []
commands:
  provides: []
  consumes: []
events:
  publishes:
    - eventType: TodoCompleted
      schemaRef: brix://schemas/todo/todo-completed/1.0.0
      version: 1.0.0
      reliability: STANDARD
  subscribes: []
tasks:
  provides: []
internalContracts:
  provides: []
data:
  storageId: todo
  schema: todo
  migrationLocation: db/migration/todo
  outbox: todo_outbox
permissions:
  - id: todo:read
    name: Read TODOs
    description: Allows reading the TODOs of the current tenant.
    grants:
      - {role: OWNER, scope: TENANT}
      - {role: ADMIN, scope: TENANT}
      - {role: MEMBER, scope: TENANT}
  - id: todo:write
    name: Change TODOs
    description: Allows creating and completing TODOs in the current tenant.
    grants:
      - {role: OWNER, scope: TENANT}
      - {role: ADMIN, scope: TENANT}
      - {role: MEMBER, scope: TENANT}
config: []
```

Every `schemaRef` names a JSON schema that the plugin ships under `META-INF/brix/schemas/todo/`. The schema of the
event must match its payload record exactly:

```json title="backend/api/resources/META-INF/brix/schemas/todo/todo-completed/1.0.0.json"
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "brix://schemas/todo/todo-completed/1.0.0",
  "type": "object",
  "additionalProperties": false,
  "required": ["tenantId", "todoId", "completedAt"],
  "properties": {
    "tenantId": { "type": "integer" },
    "todoId": { "type": "string", "maxLength": 32 },
    "completedAt": { "type": "string" }
  }
}
```

[Events and Commands](../guides/events-and-commands.md) shows how other plugins subscribe to `TodoCompleted`, and how
to check the payload against its schema in your build.

## Part 4: Implement Frontend

### 4.1 Repository Layer

```typescript title="frontend/web/repositories/TodoRepository.ts"
import { HttpCapability } from '@brix/runtime-sdk-api-web';
import { Todo, CreateTodoRequest } from '../../shared/types/Todo';

/**
 * Repository for TODO API calls.
 * 
 * Uses HttpCapability from runtime instead of fetch/axios directly.
 * This ensures infrastructure agnosticism per v3.0.7 constraint #2.
 */
export class TodoRepository {
  constructor(private http: HttpCapability) {}
  
  async getAll(): Promise<Todo[]> {
    return this.http.get<Todo[]>('/api/todos');
  }
  
  async create(request: CreateTodoRequest): Promise<Todo> {
    return this.http.post<Todo>('/api/todos', request);
  }
  
  async complete(id: string): Promise<void> {
    return this.http.patch(`/api/todos/${id}/complete`);
  }
}
```

### 4.2 ViewModel Hook

```typescript title="frontend/web/hooks/useTodoList.ts"
import { useState, useEffect, useCallback } from 'react';
import { useCapability } from '@brix/runtime-sdk-api-web';
import { HttpCapability } from '@brix/runtime-sdk-api-web';
import { TodoRepository } from '../repositories/TodoRepository';
import { Todo } from '../../shared/types/Todo';

/**
 * ViewModel hook for TODO list.
 * 
 * Follows the View -> ViewModel -> Repository pattern
 * per v3.0.7 constraint #7 (Full-Stack Separation).
 */
export function useTodoList() {
  const http = useCapability(HttpCapability);
  const [todos, setTodos] = useState<Todo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  
  const repository = new TodoRepository(http);
  
  const fetchTodos = useCallback(async () => {
    setLoading(true);
    try {
      const data = await repository.getAll();
      setTodos(data);
      setError(null);
    } catch (e) {
      setError(e as Error);
    } finally {
      setLoading(false);
    }
  }, [repository]);
  
  const createTodo = useCallback(async (title: string) => {
    const newTodo = await repository.create({ title });
    setTodos(prev => [...prev, newTodo]);
    return newTodo;
  }, [repository]);
  
  const completeTodo = useCallback(async (id: string) => {
    await repository.complete(id);
    setTodos(prev => 
      prev.map(t => t.id === id ? { ...t, completed: true } : t)
    );
  }, [repository]);
  
  useEffect(() => {
    fetchTodos();
  }, [fetchTodos]);
  
  return {
    todos,
    loading,
    error,
    createTodo,
    completeTodo,
    refresh: fetchTodos,
  };
}
```

### 4.3 View Component

```tsx title="frontend/web/pages/TodoList.tsx"
import React, { useState } from 'react';
import { useTodoList } from '../hooks/useTodoList';

/**
 * TODO List page component.
 * 
 * This is a pure view layer - it uses the useTodoList hook
 * for all data and business logic (View -> ViewModel pattern).
 */
export function TodoListPage() {
  const { todos, loading, error, createTodo, completeTodo } = useTodoList();
  const [newTitle, setNewTitle] = useState('');
  
  if (loading) return <div>Loading...</div>;
  if (error) return <div>Error: {error.message}</div>;
  
  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (newTitle.trim()) {
      await createTodo(newTitle);
      setNewTitle('');
    }
  };
  
  return (
    <div className="todo-list">
      <h1>My TODOs</h1>
      
      <form onSubmit={handleCreate}>
        <input
          type="text"
          value={newTitle}
          onChange={(e) => setNewTitle(e.target.value)}
          placeholder="What needs to be done?"
        />
        <button type="submit">Add</button>
      </form>
      
      <ul>
        {todos.map(todo => (
          <li key={todo.id}>
            <span style={{ 
              textDecoration: todo.completed ? 'line-through' : 'none' 
            }}>
              {todo.title}
            </span>
            {!todo.completed && (
              <button onClick={() => completeTodo(todo.id)}>
                Complete
              </button>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}
```

## Part 5: Architecture Tests

The scaffold generates no tests. Add the Architecture Guard to the core module, with the ArchUnit JUnit engine
that runs its rules, both at test scope:

```xml title="backend/core/pom.xml (excerpt)"
<dependency>
    <groupId>io.brix</groupId>
    <artifactId>architecture-guard</artifactId>
    <version>3.2.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit6</artifactId>
    <version>1.5.1</version>
    <scope>test</scope>
</dependency>
```

Then add one test class. It applies the guard's rule set for plugin code to the module's classes and to those of
`backend/api`, which the module depends on. Add the same class to `backend/server` to check the handlers and the
plugin class too.

```java title="backend/core/test/ArchitectureTest.java"
package com.example.todo.core;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

import io.brix.architecture.guard.BrixArchitectureRules;

/** The Architecture Guard's plugin rules, run by mvn test. */
@AnalyzeClasses(packages = "com.example.todo", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchTests brixRules = BrixArchitectureRules.pluginProfile();
}
```

The guard's rule classes, such as `AdapterIsolationRule`, are final: use `BrixArchitectureRules`, do not extend them.
The rules about other plugins and data owners only recognise plugins in the guard's plugin packages, such as the
scaffold's `io.brix.app.todo`; with this tutorial's `com.example.todo` the plugin gets the other rules.
[Architecture Tests](../guides/plugin-development.md#42-architecture-tests) in the Plugin Development Guide shows
how to add rules of your own.

Run the tests:

```bash
cd backend
mvn test
```

A class that breaks a rule, for example one that imports a Kafka client, fails the build with the rule's reason.

## Part 6: Run and Test

A plugin is not a Spring Boot application. It has no `main` method, so there is nothing to `spring-boot:run`. A host
runs it:
- the host is a Spring Boot application with no business code: its dependencies, its properties and a boot class;
- the plugin's server artifact is on the host's class path, and the runtime finds the plugin through its services
  file;
- the runtime validates the manifest, runs the migrations, creates the handlers and opens the endpoints.

`sample-e2e` in the repository is such a host. `SampleHost` and the properties of `SamplePluginsEndToEndTest` run the
two sample plugins on PostgreSQL and Kafka; see [Running it end to end](../examples/event-driven-plugin.md#running-it-end-to-end).
Run the TODO plugin the same way:

1. Install the plugin's artifacts:

   ```bash
   cd backend
   mvn install
   ```

2. In the host's `pom.xml`, depend on `com.example:todo-server`; it brings `todo-core` and `todo-api`. Add what
   `sample-e2e`'s `pom.xml` lists next to its plugins:
   - the runtime, `io.brix:runtime-orchestrator`;
   - the adapters `infra-adapter-database`, `infra-adapter-persistence-jpa`, `infra-adapter-outbox`,
     `infra-adapter-kafka-core` and `infra-adapter-idgen` (group `io.brix`);
   - `spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa` and `spring-boot-starter-actuator`;
   - `flyway-core`, `flyway-database-postgresql` and the PostgreSQL driver.
3. Configure the host as `sample-e2e` does:

   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/todo
   spring.datasource.username=todo
   spring.datasource.password=todo
   spring.jpa.open-in-view=false
   brix.capability.required[0]=io.runtime.sdk.capability.EventBusCapability
   brix.capability.required[1]=io.runtime.sdk.capability.IdGeneratorCapability
   brix.runtime-shell.required-plugins[0]=todo
   brix.infra.database.migration.fail-on-error=true
   brix.outbox.relay.enabled=true
   brix.outbox.relay.persistence.enabled=true
   brix.outbox.relay.owner=todo-host
   brix.outbox.relay.transport=kafka
   brix.infra.kafka.bootstrap-servers=localhost:9092
   ```

   - `required-plugins` makes the plugin part of the host: the host is not ready without it.
   - The relay takes `TodoCompleted` from `todo_outbox` and publishes it to Kafka.
   - The platform creates `todo_outbox` from its own migrations, which are written for PostgreSQL.
4. Give the host an identity provider. The endpoints require a signed-in tenant member (`tenant-actor`).
   `SampleHost` stands in for one with a single `AuthCapability` bean.
5. Start the host, then the frontend:

   ```bash
   # Terminal 1: the host
   cd todo-host
   mvn spring-boot:run

   # Terminal 2: the frontend
   cd frontend/web
   pnpm dev
   ```

[Host Assembly](../concepts/host-assembly.md) describes hosts in general.

## Summary

You've created a complete plugin following Brix architecture:

| Layer | What You Built |
|-------|----------------|
| **Shared** | Todo types, events |
| **Backend Core** | Todo entity and repository, TodoService (uses capabilities), TodoCompleted event |
| **Backend Server** | Endpoint handlers, plugin class, manifest, migration |
| **Frontend Repository** | API calls via HttpCapability |
| **Frontend ViewModel** | useTodoList hook |
| **Frontend View** | TodoListPage component |

The Architecture Guard's plugin rules pass, so your plugin is:
- Infrastructure agnostic
- Portable between Standalone/Embedded modes
- Easily testable with mocks

## Next Steps

- [Event Model](../concepts/event-model) - Publish events across plugins
- [Testing Guide](../guides/testing) - Write effective tests
- [Deployment Guide](../guides/deployment) - Deploy to production
