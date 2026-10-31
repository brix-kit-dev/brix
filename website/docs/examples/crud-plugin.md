---
id: crud-plugin
title: CRUD Plugin Example
sidebar_label: CRUD Plugin
sidebar_position: 2
---

# CRUD Plugin Example

A complete CRUD (Create, Read, Update, Delete) plugin demonstrating persistent data operations.

:::note
The handlers on this page use the shape from before 3.3.0, which still works. A new handler implements
`TypedEndpointHandler`, lets the request schema refuse bad input, answers lists with `Page`, and fails with a
`BrixException` whose code names its category. See [Endpoints](../guides/endpoints.md).
:::

## Overview

This example builds a **Task Management** plugin with:
- Task creation, listing, updating, and deletion
- Pagination and filtering
- Form validation
- Optimistic UI updates

## Backend Implementation

The backend is a plugin of three Maven modules (see
[Project structure](../guides/backend-development.md#project-structure)):

```text
task-plugin-api/      plugin-api: enums, commands, queries and the views the endpoints return
task-plugin-core/     plugin-core: the Task entity, its repository and TaskService
task-plugin-server/   plugin-server: TaskPlugin, the endpoint handlers, the manifest, the migration
```

It serves the five routes the frontend below calls. The runtime does the rest for every request:
- it checks the caller's token and permission;
- it binds the caller's tenant;
- it runs the handler in a unit of work: read-only for `GET`, read-write otherwise.

### Domain Model

```java
// task-plugin-api/src/main/java/com/example/task/api/TaskStatus.java and TaskPriority.java
package com.example.task.api;

public enum TaskStatus {
    TODO, IN_PROGRESS, DONE, CANCELLED
}

public enum TaskPriority {
    LOW, MEDIUM, HIGH, URGENT
}
```

The entity follows the persistence rules:
- the id comes from `IdGeneratorCapability`, never from `@GeneratedValue`;
- every row has a `tenant_id`;
- `Persistable` tells Spring Data that a task with an assigned id is new.

```java
// task-plugin-core/src/main/java/com/example/task/core/Task.java
package com.example.task.core;

import java.time.OffsetDateTime;

import com.example.task.api.CreateTaskCommand;
import com.example.task.api.TaskPriority;
import com.example.task.api.TaskStatus;
import com.example.task.api.UpdateTaskCommand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "task_task")
public class Task implements Persistable<Long> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private TaskPriority priority;

    @Column(name = "assignee_id", length = 64)
    private String assigneeId;

    @Column(name = "due_date")
    private OffsetDateTime dueDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Transient
    private boolean fresh = true;

    protected Task() {
    }

    public static Task create(long id, long tenantId, CreateTaskCommand command, OffsetDateTime now) {
        Task task = new Task();
        task.id = id;
        task.tenantId = tenantId;
        task.title = command.title();
        task.description = command.description();
        task.status = TaskStatus.TODO;
        task.priority = command.priority() != null ? command.priority() : TaskPriority.MEDIUM;
        task.assigneeId = command.assigneeId();
        task.dueDate = command.dueDate();
        task.createdAt = now;
        task.updatedAt = now;
        return task;
    }

    /** Applies the fields the command sets; a field it leaves null keeps its value. */
    public void update(UpdateTaskCommand command, OffsetDateTime now) {
        if (command.title() != null) {
            title = command.title();
        }
        if (command.description() != null) {
            description = command.description();
        }
        if (command.status() != null) {
            status = command.status();
        }
        if (command.priority() != null) {
            priority = command.priority();
        }
        if (command.assigneeId() != null) {
            assigneeId = command.assigneeId();
        }
        if (command.dueDate() != null) {
            dueDate = command.dueDate();
        }
        updatedAt = now;
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

    // Getters for title, description, status, priority, assigneeId, dueDate, createdAt and updatedAt
}
```

### Commands and Queries

```java
// task-plugin-api/src/main/java/com/example/task/api: commands
package com.example.task.api;

public record CreateTaskCommand(
    String title,
    String description,
    TaskPriority priority,
    String assigneeId,
    OffsetDateTime dueDate
) {}

public record UpdateTaskCommand(
    String title,
    String description,
    TaskStatus status,
    TaskPriority priority,
    String assigneeId,
    OffsetDateTime dueDate
) {}

// Query parameters
public record TaskQuery(
    TaskStatus status,
    TaskPriority priority,
    String assigneeId,
    int page,
    int size
) {
    public TaskQuery {
        if (page < 1) page = 1;
        if (size < 1) size = 10;
        if (size > 100) size = 100;
    }
}

// What the endpoints return: the JSON the frontend reads
public record TaskView(
    String id,
    String title,
    String description,
    TaskStatus status,
    TaskPriority priority,
    String assigneeId,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime dueDate
) {}

public record TaskPage(List<TaskView> items, int page, int size, long totalItems, int totalPages) {}
```

### Repository

```java
// task-plugin-core/src/main/java/com/example/task/core/TaskRepository.java
package com.example.task.core;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Reached only through PersistenceCapability.repository(...); the adapter keeps it inside the caller's tenant. */
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {
}
```

### Service

```java
// task-plugin-core/src/main/java/com/example/task/core/TaskService.java
package com.example.task.core;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.example.task.api.CreateTaskCommand;
import com.example.task.api.TaskQuery;
import com.example.task.api.UpdateTaskCommand;
import io.runtime.sdk.capability.IdGeneratorCapability;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * Task management: a plain class that a handler factory creates. Every call runs in the unit of work the
 * runtime opened for the handler, in the caller's tenant, so no query names a tenant.
 */
public final class TaskService {

    private final TaskRepository tasks;
    private final IdGeneratorCapability ids;
    private final Clock clock;

    public TaskService(TaskRepository tasks, IdGeneratorCapability ids, Clock clock) {
        this.tasks = Objects.requireNonNull(tasks, "tasks must not be null");
        this.ids = Objects.requireNonNull(ids, "ids must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public Task create(long tenantId, CreateTaskCommand command) {
        return tasks.save(Task.create(Long.parseLong(ids.generate()), tenantId, command, OffsetDateTime.now(clock)));
    }

    public Optional<Task> find(long id) {
        return tasks.findById(id);
    }

    public Page<Task> search(TaskQuery query) {
        Specification<Task> filter = (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.status() != null) {
                predicates.add(builder.equal(root.get("status"), query.status()));
            }
            if (query.priority() != null) {
                predicates.add(builder.equal(root.get("priority"), query.priority()));
            }
            if (query.assigneeId() != null) {
                predicates.add(builder.equal(root.get("assigneeId"), query.assigneeId()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return tasks.findAll(filter,
            PageRequest.of(query.page() - 1, query.size(), Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    /** Changes a task; the change is written when the unit of work commits. */
    public Optional<Task> update(long id, UpdateTaskCommand command) {
        Optional<Task> task = tasks.findById(id);
        task.ifPresent(found -> found.update(command, OffsetDateTime.now(clock)));
        return task;
    }

    public boolean delete(long id) {
        Optional<Task> task = tasks.findById(id);
        task.ifPresent(tasks::delete);
        return task.isPresent();
    }
}
```

`findById` of a task in another tenant finds nothing: the persistence adapter limits every read and write to the
tenant of the unit of work.

### Migration

```sql
-- task-plugin-server/src/main/resources/db/migration/task/V1__task.sql
CREATE TABLE task_task (
    id           BIGINT         NOT NULL,
    tenant_id    BIGINT         NOT NULL,
    title        VARCHAR(200)   NOT NULL,
    description  VARCHAR(2000),
    status       VARCHAR(20)    NOT NULL,
    priority     VARCHAR(20)    NOT NULL,
    assignee_id  VARCHAR(64),
    due_date     TIMESTAMPTZ,
    created_at   TIMESTAMPTZ    NOT NULL,
    updated_at   TIMESTAMPTZ    NOT NULL,
    CONSTRAINT pk_task_task PRIMARY KEY (id),
    CONSTRAINT chk_task_task_status CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE', 'CANCELLED')),
    CONSTRAINT chk_task_task_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'))
);

CREATE INDEX idx_task_task_tenant ON task_task (tenant_id, created_at);
```

`assignee_id` holds a member's ID as a plain value, with no foreign key to platform tables.

### Endpoints

Each route is declared in the manifest. Here is the first one, with the plugin's capabilities, data and
permissions:

```yaml
# task-plugin-server/src/main/resources/META-INF/brix/plugin-manifest.yaml (excerpt)
endpoints:
  provides:
    - endpointId: task.create.v1
      transport: http
      method: POST
      path: /api/tasks
      request:
        schemaRef: brix://schemas/task/create-task-request/1.0.0
      response:
        schemaRef: brix://schemas/task/task/1.0.0
      handlerId: task.create.v1
      authentication:
        mode: required
        policyRef: tenant-actor
      authorization:
        permissions:
          - task:write
        decision: all
        resourcePolicyRef: task-write
      tenantContext:
        mode: required
        source: runtime
      timeout:
        policyRef: endpoint-default
      idempotency:
        mode: not-applicable
    # task.get.v1, task.search.v1, task.update.v1 and task.delete.v1 have the same fields
capabilities:
  required:
    - id: io.runtime.sdk.capability.PersistenceCapability
      version: ">=3.2.0 <4.0.0"
    - id: io.runtime.sdk.capability.IdGeneratorCapability
      version: ">=3.2.0 <4.0.0"
  optional: []
data:
  storageId: task
  schema: task
  migrationLocation: db/migration/task
permissions:
  - id: task:read
    name: Read tasks
    description: Allows reading the tasks of the current tenant.
    grants:
      - {role: OWNER, scope: TENANT}
      - {role: ADMIN, scope: TENANT}
      - {role: MEMBER, scope: TENANT}
  - id: task:write
    name: Change tasks
    description: Allows creating, changing and deleting tasks in the current tenant.
    grants:
      - {role: OWNER, scope: TENANT}
      - {role: ADMIN, scope: TENANT}
      - {role: MEMBER, scope: TENANT}
```

| `endpointId` and `handlerId` | Method | Path | Permission | Handler |
|---|---|---|---|---|
| `task.create.v1` | `POST` | `/api/tasks` | `task:write` | `TaskEndpoints.Create` |
| `task.get.v1` | `GET` | `/api/tasks/{id}` | `task:read` | `TaskEndpoints.Get` |
| `task.search.v1` | `GET` | `/api/tasks` | `task:read` | `TaskEndpoints.Search` |
| `task.update.v1` | `PUT` | `/api/tasks/{id}` | `task:write` | `TaskEndpoints.Update` |
| `task.delete.v1` | `DELETE` | `/api/tasks/{id}` | `task:write` | `TaskEndpoints.Delete` |

Each endpoint names its own request and response schema under `brix://schemas/task/`. The plugin ships these
schema files under `META-INF/brix/schemas/task/`.

### Endpoint Handlers

One small handler class per endpoint:

```java
// task-plugin-server/src/main/java/com/example/task/server/TaskEndpoints.java
package com.example.task.server;

import java.util.Objects;

import com.example.task.api.TaskPage;
import com.example.task.api.TaskQuery;
import com.example.task.api.TaskView;
import com.example.task.core.Task;
import com.example.task.core.TaskService;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.EndpointResponse;
import org.springframework.data.domain.Page;

final class TaskEndpoints {

    private TaskEndpoints() {
    }

    /** POST /api/tasks: 201 with the new task. */
    static final class Create implements EndpointHandler<EndpointInvocation<Object>, EndpointResponse> {

        private final TaskService tasks;

        Create(TaskService tasks) {
            this.tasks = Objects.requireNonNull(tasks, "tasks must not be null");
        }

        @Override
        public EndpointResponse handle(EndpointInvocation<Object> request) {
            long tenantId = Long.parseLong(request.tenantId().orElseThrow());
            return EndpointResponse.created(view(tasks.create(tenantId, TaskRequests.create(request))));
        }
    }

    /** GET /api/tasks/{id}: 200 with the task, or 404. */
    static final class Get implements EndpointHandler<EndpointInvocation<Object>, TaskView> {

        private final TaskService tasks;

        Get(TaskService tasks) {
            this.tasks = Objects.requireNonNull(tasks, "tasks must not be null");
        }

        @Override
        public TaskView handle(EndpointInvocation<Object> request) {
            return tasks.find(TaskRequests.id(request)).map(TaskEndpoints::view).orElseThrow(TaskRequests::notFound);
        }
    }

    /** GET /api/tasks?status=&priority=&assigneeId=&page=&size=: 200 with one page. */
    static final class Search implements EndpointHandler<EndpointInvocation<Object>, TaskPage> {

        private final TaskService tasks;

        Search(TaskService tasks) {
            this.tasks = Objects.requireNonNull(tasks, "tasks must not be null");
        }

        @Override
        public TaskPage handle(EndpointInvocation<Object> request) {
            TaskQuery query = TaskRequests.query(request);
            Page<Task> page = tasks.search(query);
            return new TaskPage(page.getContent().stream().map(TaskEndpoints::view).toList(),
                query.page(), query.size(), page.getTotalElements(), page.getTotalPages());
        }
    }

    /** PUT /api/tasks/{id}: 200 with the changed task, or 404. */
    static final class Update implements EndpointHandler<EndpointInvocation<Object>, TaskView> {

        private final TaskService tasks;

        Update(TaskService tasks) {
            this.tasks = Objects.requireNonNull(tasks, "tasks must not be null");
        }

        @Override
        public TaskView handle(EndpointInvocation<Object> request) {
            return tasks.update(TaskRequests.id(request), TaskRequests.update(request))
                .map(TaskEndpoints::view)
                .orElseThrow(TaskRequests::notFound);
        }
    }

    /** DELETE /api/tasks/{id}: 204, or 404. */
    static final class Delete implements EndpointHandler<EndpointInvocation<Object>, EndpointResponse> {

        private final TaskService tasks;

        Delete(TaskService tasks) {
            this.tasks = Objects.requireNonNull(tasks, "tasks must not be null");
        }

        @Override
        public EndpointResponse handle(EndpointInvocation<Object> request) {
            if (!tasks.delete(TaskRequests.id(request))) {
                throw TaskRequests.notFound();
            }
            return new EndpointResponse(204, null);
        }
    }

    private static TaskView view(Task task) {
        return new TaskView(String.valueOf(task.getId()), task.getTitle(), task.getDescription(), task.getStatus(),
            task.getPriority(), task.getAssigneeId(), task.getCreatedAt(), task.getUpdatedAt(), task.getDueDate());
    }
}
```

`TaskRequests` reads each request. Anything that is not a task is refused with a 400 before the service runs:

```java
// task-plugin-server/src/main/java/com/example/task/server/TaskRequests.java
package com.example.task.server;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import com.example.task.api.CreateTaskCommand;
import com.example.task.api.TaskPriority;
import com.example.task.api.TaskQuery;
import com.example.task.api.TaskStatus;
import com.example.task.api.UpdateTaskCommand;
import io.runtime.sdk.plugin.EndpointHandlingException;
import io.runtime.sdk.plugin.EndpointInvocation;

final class TaskRequests {

    private TaskRequests() {
    }

    static CreateTaskCommand create(EndpointInvocation<Object> request) {
        Map<?, ?> body = body(request);
        String title = text(body.get("title"));
        if (title == null || title.length() > 200) {
            throw invalid("A task needs a title of at most 200 characters");
        }
        return new CreateTaskCommand(title, text(body.get("description")),
            constant(TaskPriority.class, body.get("priority")), text(body.get("assigneeId")),
            time(body.get("dueDate")));
    }

    static UpdateTaskCommand update(EndpointInvocation<Object> request) {
        Map<?, ?> body = body(request);
        String title = text(body.get("title"));
        if (title != null && title.length() > 200) {
            throw invalid("A title has at most 200 characters");
        }
        return new UpdateTaskCommand(title, text(body.get("description")),
            constant(TaskStatus.class, body.get("status")), constant(TaskPriority.class, body.get("priority")),
            text(body.get("assigneeId")), time(body.get("dueDate")));
    }

    static TaskQuery query(EndpointInvocation<Object> request) {
        Map<String, List<String>> parameters = request.queryParameters();
        return new TaskQuery(
            constant(TaskStatus.class, first(parameters, "status")),
            constant(TaskPriority.class, first(parameters, "priority")),
            text(first(parameters, "assigneeId")),
            number(first(parameters, "page"), 1),
            number(first(parameters, "size"), 10));
    }

    /** The {id} path variable; a value that is not a number names no task. */
    static long id(EndpointInvocation<Object> request) {
        try {
            return Long.parseLong(request.pathVariables().get("id"));
        } catch (NumberFormatException notAnId) {
            throw notFound();
        }
    }

    static EndpointHandlingException notFound() {
        return new EndpointHandlingException(404, "TASK_NOT_FOUND", "No such task");
    }

    private static Map<?, ?> body(EndpointInvocation<Object> request) {
        return request.body() instanceof Map<?, ?> map ? map : Map.of();
    }

    private static String first(Map<String, List<String>> parameters, String name) {
        List<String> values = parameters.getOrDefault(name, List.of());
        return values.isEmpty() ? null : values.get(0);
    }

    private static String text(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private static <E extends Enum<E>> E constant(Class<E> type, Object value) {
        String text = text(value);
        try {
            return text == null ? null : Enum.valueOf(type, text);
        } catch (IllegalArgumentException unknown) {
            throw invalid("Unknown " + type.getSimpleName());
        }
    }

    private static OffsetDateTime time(Object value) {
        String text = text(value);
        try {
            return text == null ? null : OffsetDateTime.parse(text);
        } catch (DateTimeParseException notATime) {
            throw invalid("Dates must be ISO-8601 date-times");
        }
    }

    private static int number(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException notANumber) {
            throw invalid("page and size must be numbers");
        }
    }

    private static EndpointHandlingException invalid(String message) {
        return new EndpointHandlingException(400, "TASK_INVALID", message);
    }
}
```

### Plugin Class

The plugin class binds a factory to each handler ID. The factories get the repository and the ID generator from the
plugin's context. `META-INF/services/io.runtime.sdk.plugin.BrixPlugin` names this class.

```java
// task-plugin-server/src/main/java/com/example/task/server/TaskPlugin.java
package com.example.task.server;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;

import com.example.task.core.TaskRepository;
import com.example.task.core.TaskService;
import io.runtime.sdk.capability.IdGeneratorCapability;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.plugin.BrixHealth;
import io.runtime.sdk.plugin.BrixPlugin;
import io.runtime.sdk.plugin.PluginBootstrapContext;
import io.runtime.sdk.plugin.PluginContext;

public final class TaskPlugin implements BrixPlugin {

    private final AtomicReference<BrixHealth> health = new AtomicReference<>(BrixHealth.down("not started"));

    @Override
    public void configure(PluginBootstrapContext bootstrap) {
        bootstrap.bindEndpointHandlerFactory("task.create.v1", context -> new TaskEndpoints.Create(tasks(context)));
        bootstrap.bindEndpointHandlerFactory("task.get.v1", context -> new TaskEndpoints.Get(tasks(context)));
        bootstrap.bindEndpointHandlerFactory("task.search.v1", context -> new TaskEndpoints.Search(tasks(context)));
        bootstrap.bindEndpointHandlerFactory("task.update.v1", context -> new TaskEndpoints.Update(tasks(context)));
        bootstrap.bindEndpointHandlerFactory("task.delete.v1", context -> new TaskEndpoints.Delete(tasks(context)));
    }

    private static TaskService tasks(PluginContext context) {
        return new TaskService(
            context.require(PersistenceCapability.class).repository(TaskRepository.class),
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

To tell other plugins that a task changed, publish an integration event from `TaskService`; see
[Events and Commands](../guides/events-and-commands.md).

## Frontend Implementation

### Repository

```typescript
// task-plugin-web/src/repositories/TaskRepository.ts
import { useMemo } from '@brix/shared-runtime-web';
import { useCapability, HttpCapability } from '@brix/runtime-sdk-api-web';
import type { Task, CreateTaskCommand, UpdateTaskCommand, TaskQuery, Page } from '../shared/types';

class TaskRepository {
  constructor(private http: HttpCapability) {}
  
  async create(command: CreateTaskCommand): Promise<Task> {
    return this.http.post<Task>('/api/tasks', command);
  }
  
  async findById(id: string): Promise<Task> {
    return this.http.get<Task>(`/api/tasks/${id}`);
  }
  
  async findAll(query: TaskQuery): Promise<Page<Task>> {
    const params = new URLSearchParams();
    if (query.status) params.set('status', query.status);
    if (query.priority) params.set('priority', query.priority);
    if (query.assigneeId) params.set('assigneeId', query.assigneeId);
    params.set('page', String(query.page));
    params.set('size', String(query.size));
    
    return this.http.get<Page<Task>>(`/api/tasks?${params}`);
  }
  
  async update(id: string, command: UpdateTaskCommand): Promise<Task> {
    return this.http.put<Task>(`/api/tasks/${id}`, command);
  }
  
  async delete(id: string): Promise<void> {
    return this.http.delete(`/api/tasks/${id}`);
  }
}

export function useTaskRepository(): TaskRepository {
  const http = useCapability(HttpCapability);
  return useMemo(() => new TaskRepository(http), [http]);
}
```

### ViewModel - Task List

```typescript
// task-plugin-web/src/viewmodels/useTaskList.ts
import { useState, useEffect, useCallback } from '@brix/shared-runtime-web';
import { useTaskRepository } from '../repositories/TaskRepository';
import type { Task, TaskQuery, TaskStatus, TaskPriority } from '../shared/types';

interface UseTaskListResult {
  tasks: Task[];
  isLoading: boolean;
  error: Error | null;
  page: number;
  totalPages: number;
  filters: TaskFilters;
  setFilters: (filters: Partial<TaskFilters>) => void;
  goToPage: (page: number) => void;
  refresh: () => void;
  deleteTask: (id: string) => Promise<void>;
  updateTaskStatus: (id: string, status: TaskStatus) => Promise<void>;
}

interface TaskFilters {
  status: TaskStatus | null;
  priority: TaskPriority | null;
  assigneeId: string | null;
}

export function useTaskList(): UseTaskListResult {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(0);
  const [filters, setFiltersState] = useState<TaskFilters>({
    status: null,
    priority: null,
    assigneeId: null,
  });
  
  const repository = useTaskRepository();
  
  const loadTasks = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    
    try {
      const query: TaskQuery = {
        ...filters,
        page,
        size: 10,
      };
      
      const result = await repository.findAll(query);
      setTasks(result.items);
      setTotalPages(result.totalPages);
    } catch (e) {
      setError(e instanceof Error ? e : new Error(String(e)));
    } finally {
      setIsLoading(false);
    }
  }, [repository, page, filters]);
  
  useEffect(() => {
    loadTasks();
  }, [loadTasks]);
  
  const setFilters = useCallback((newFilters: Partial<TaskFilters>) => {
    setFiltersState(prev => ({ ...prev, ...newFilters }));
    setPage(1); // Reset to first page on filter change
  }, []);
  
  const goToPage = useCallback((newPage: number) => {
    if (newPage >= 1 && newPage <= totalPages) {
      setPage(newPage);
    }
  }, [totalPages]);
  
  const deleteTask = useCallback(async (id: string) => {
    // Optimistic update
    setTasks(prev => prev.filter(t => t.id !== id));
    
    try {
      await repository.delete(id);
    } catch (e) {
      // Rollback on error
      loadTasks();
      throw e;
    }
  }, [repository, loadTasks]);
  
  const updateTaskStatus = useCallback(async (id: string, status: TaskStatus) => {
    // Optimistic update
    setTasks(prev => prev.map(t => 
      t.id === id ? { ...t, status } : t
    ));
    
    try {
      await repository.update(id, { status });
    } catch (e) {
      loadTasks();
      throw e;
    }
  }, [repository, loadTasks]);
  
  return {
    tasks,
    isLoading,
    error,
    page,
    totalPages,
    filters,
    setFilters,
    goToPage,
    refresh: loadTasks,
    deleteTask,
    updateTaskStatus,
  };
}
```

### ViewModel - Task Form

```typescript
// task-plugin-web/src/viewmodels/useTaskForm.ts
import { useState, useCallback } from '@brix/shared-runtime-web';
import { useTaskRepository } from '../repositories/TaskRepository';
import type { Task, CreateTaskCommand, TaskPriority } from '../shared/types';

interface FormData {
  title: string;
  description: string;
  priority: TaskPriority;
  assigneeId: string;
  dueDate: string;
}

interface FormErrors {
  title?: string;
  description?: string;
  dueDate?: string;
  _form?: string;
}

interface UseTaskFormResult {
  formData: FormData;
  errors: FormErrors;
  isSubmitting: boolean;
  setField: <K extends keyof FormData>(field: K, value: FormData[K]) => void;
  submit: () => Promise<Task | null>;
  reset: () => void;
}

const initialData: FormData = {
  title: '',
  description: '',
  priority: 'MEDIUM',
  assigneeId: '',
  dueDate: '',
};

export function useTaskForm(): UseTaskFormResult {
  const [formData, setFormData] = useState<FormData>(initialData);
  const [errors, setErrors] = useState<FormErrors>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  
  const repository = useTaskRepository();
  
  const validate = useCallback((): boolean => {
    const newErrors: FormErrors = {};
    
    if (!formData.title.trim()) {
      newErrors.title = 'Title is required';
    } else if (formData.title.length > 200) {
      newErrors.title = 'Title must be 200 characters or less';
    }
    
    if (formData.dueDate && new Date(formData.dueDate) < new Date()) {
      newErrors.dueDate = 'Due date must be in the future';
    }
    
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  }, [formData]);
  
  const setField = useCallback(<K extends keyof FormData>(
    field: K, 
    value: FormData[K]
  ) => {
    setFormData(prev => ({ ...prev, [field]: value }));
    setErrors(prev => ({ ...prev, [field]: undefined }));
  }, []);
  
  const submit = useCallback(async (): Promise<Task | null> => {
    if (!validate()) return null;
    
    setIsSubmitting(true);
    try {
      const command: CreateTaskCommand = {
        title: formData.title,
        description: formData.description || null,
        priority: formData.priority,
        assigneeId: formData.assigneeId || null,
        dueDate: formData.dueDate ? new Date(formData.dueDate).toISOString() : null,
      };
      
      return await repository.create(command);
    } catch (e) {
      setErrors({ _form: e instanceof Error ? e.message : 'Failed to create task' });
      return null;
    } finally {
      setIsSubmitting(false);
    }
  }, [formData, repository, validate]);
  
  const reset = useCallback(() => {
    setFormData(initialData);
    setErrors({});
  }, []);
  
  return { formData, errors, isSubmitting, setField, submit, reset };
}
```

### View - Task List

```tsx
// task-plugin-web/src/views/TaskListPage.tsx
import { React } from '@brix/shared-runtime-web';
import { useTaskList } from '../viewmodels/useTaskList';
import { TaskTable } from './components/TaskTable';
import { TaskFilters } from './components/TaskFilters';
import { Pagination } from './components/Pagination';
import { LoadingSpinner, ErrorMessage } from '@brix/platform-commons-web';

export function TaskListPage() {
  const {
    tasks,
    isLoading,
    error,
    page,
    totalPages,
    filters,
    setFilters,
    goToPage,
    refresh,
    deleteTask,
    updateTaskStatus,
  } = useTaskList();
  
  if (error) {
    return <ErrorMessage message={error.message} onRetry={refresh} />;
  }
  
  return (
    <div className="task-list-page">
      <header className="page-header">
        <h1>Tasks</h1>
        <a href="/tasks/new" className="btn-primary">New Task</a>
      </header>
      
      <TaskFilters filters={filters} onChange={setFilters} />
      
      {isLoading ? (
        <LoadingSpinner />
      ) : (
        <>
          <TaskTable
            tasks={tasks}
            onDelete={deleteTask}
            onStatusChange={updateTaskStatus}
          />
          
          <Pagination
            page={page}
            totalPages={totalPages}
            onPageChange={goToPage}
          />
        </>
      )}
    </div>
  );
}
```

### View - Task Form

```tsx
// task-plugin-web/src/views/CreateTaskPage.tsx
import { React } from '@brix/shared-runtime-web';
import { useTaskForm } from '../viewmodels/useTaskForm';
import { useNavigate } from '@brix/runtime-sdk-api-web';

export function CreateTaskPage() {
  const { formData, errors, isSubmitting, setField, submit, reset } = useTaskForm();
  const navigate = useNavigate();
  
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const task = await submit();
    if (task) {
      navigate(`/tasks/${task.id}`);
    }
  };
  
  return (
    <div className="create-task-page">
      <h1>Create Task</h1>
      
      <form onSubmit={handleSubmit}>
        {errors._form && (
          <div className="form-error">{errors._form}</div>
        )}
        
        <div className="form-group">
          <label htmlFor="title">Title *</label>
          <input
            id="title"
            type="text"
            value={formData.title}
            onChange={(e) => setField('title', e.target.value)}
            className={errors.title ? 'error' : ''}
          />
          {errors.title && <span className="field-error">{errors.title}</span>}
        </div>
        
        <div className="form-group">
          <label htmlFor="description">Description</label>
          <textarea
            id="description"
            value={formData.description}
            onChange={(e) => setField('description', e.target.value)}
            rows={4}
          />
        </div>
        
        <div className="form-group">
          <label htmlFor="priority">Priority</label>
          <select
            id="priority"
            value={formData.priority}
            onChange={(e) => setField('priority', e.target.value as any)}
          >
            <option value="LOW">Low</option>
            <option value="MEDIUM">Medium</option>
            <option value="HIGH">High</option>
            <option value="URGENT">Urgent</option>
          </select>
        </div>
        
        <div className="form-group">
          <label htmlFor="dueDate">Due Date</label>
          <input
            id="dueDate"
            type="date"
            value={formData.dueDate}
            onChange={(e) => setField('dueDate', e.target.value)}
            className={errors.dueDate ? 'error' : ''}
          />
          {errors.dueDate && <span className="field-error">{errors.dueDate}</span>}
        </div>
        
        <div className="form-actions">
          <button type="button" onClick={reset} disabled={isSubmitting}>
            Reset
          </button>
          <button type="submit" className="btn-primary" disabled={isSubmitting}>
            {isSubmitting ? 'Creating...' : 'Create Task'}
          </button>
        </div>
      </form>
    </div>
  );
}
```

## Key Patterns

1. **Repository pattern** - Centralized data access
2. **Optimistic updates** - Immediate UI feedback
3. **Form validation** - Client-side validation with error display
4. **Pagination** - Efficient data loading
5. **Filtering** - Dynamic query building

## Next Steps

- [Event-Driven Plugin](./event-driven-plugin) - Cross-plugin communication
- [Hello Plugin](./hello-plugin) - Minimal example
