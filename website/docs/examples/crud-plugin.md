---
id: crud-plugin
title: CRUD Plugin Example
sidebar_label: CRUD Plugin
sidebar_position: 2
---

# CRUD Plugin Example

A complete CRUD (Create, Read, Update, Delete) plugin demonstrating persistent data operations.

## Overview

This example builds a **Task Management** plugin with:
- Task creation, listing, updating, and deletion
- Pagination and filtering
- Form validation
- Optimistic UI updates

## Backend Implementation

### Domain Model

```java
// task-plugin-shared/src/main/java/com/example/task/shared/Task.java
package com.example.task.shared;

import java.time.Instant;

public record Task(
    String id,
    String title,
    String description,
    TaskStatus status,
    TaskPriority priority,
    String assigneeId,
    Instant createdAt,
    Instant updatedAt,
    Instant dueDate
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String title;
        private String description;
        private TaskStatus status = TaskStatus.TODO;
        private TaskPriority priority = TaskPriority.MEDIUM;
        private String assigneeId;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant dueDate;
        
        public Builder id(String id) { this.id = id; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder status(TaskStatus status) { this.status = status; return this; }
        public Builder priority(TaskPriority priority) { this.priority = priority; return this; }
        public Builder assigneeId(String assigneeId) { this.assigneeId = assigneeId; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder dueDate(Instant dueDate) { this.dueDate = dueDate; return this; }
        
        public Task build() {
            return new Task(id, title, description, status, priority, 
                           assigneeId, createdAt, updatedAt, dueDate);
        }
    }
}

public enum TaskStatus {
    TODO, IN_PROGRESS, DONE, CANCELLED
}

public enum TaskPriority {
    LOW, MEDIUM, HIGH, URGENT
}
```

### Commands and Queries

```java
// Commands
package com.example.task.shared;

public record CreateTaskCommand(
    String title,
    String description,
    TaskPriority priority,
    String assigneeId,
    Instant dueDate
) {}

public record UpdateTaskCommand(
    String title,
    String description,
    TaskStatus status,
    TaskPriority priority,
    String assigneeId,
    Instant dueDate
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
```

### Service

```java
// task-plugin-core/src/main/java/com/example/task/service/TaskService.java
package com.example.task.service;

import com.example.task.shared.*;
import io.brix.runtime.sdk.api.DataAccessCapability;
import io.brix.runtime.sdk.api.EventBusCapability;
import io.brix.platform.commons.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class TaskService {
    
    private final DataAccessCapability dataAccess;
    private final EventBusCapability eventBus;
    
    public TaskService(DataAccessCapability dataAccess, EventBusCapability eventBus) {
        this.dataAccess = dataAccess;
        this.eventBus = eventBus;
    }
    
    /**
     * Creates a new task.
     */
    @Transactional
    public Task createTask(CreateTaskCommand command) {
        validate(command);
        
        Task task = Task.builder()
            .id(UUID.randomUUID().toString())
            .title(command.title())
            .description(command.description())
            .status(TaskStatus.TODO)
            .priority(command.priority())
            .assigneeId(command.assigneeId())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .dueDate(command.dueDate())
            .build();
        
        Task saved = dataAccess.save(task);
        
        eventBus.publish(new TaskCreatedEvent(saved.id(), saved.title()));
        
        return saved;
    }
    
    /**
     * Finds a task by ID.
     */
    public Optional<Task> findById(String id) {
        return Optional.ofNullable(dataAccess.findById(Task.class, id));
    }
    
    /**
     * Lists tasks with pagination and filtering.
     */
    public Page<Task> findTasks(TaskQuery query) {
        Map<String, Object> criteria = buildCriteria(query);
        
        List<Task> tasks = dataAccess.findBy(Task.class, criteria);
        long total = dataAccess.count(Task.class, criteria);
        
        // Apply pagination
        int start = (query.page() - 1) * query.size();
        int end = Math.min(start + query.size(), tasks.size());
        List<Task> page = tasks.subList(start, end);
        
        return new Page<>(page, query.page(), query.size(), total);
    }
    
    /**
     * Updates an existing task.
     */
    @Transactional
    public Task updateTask(String id, UpdateTaskCommand command) {
        Task existing = findById(id)
            .orElseThrow(() -> new TaskNotFoundException(id));
        
        Task updated = Task.builder()
            .id(existing.id())
            .title(command.title() != null ? command.title() : existing.title())
            .description(command.description() != null ? command.description() : existing.description())
            .status(command.status() != null ? command.status() : existing.status())
            .priority(command.priority() != null ? command.priority() : existing.priority())
            .assigneeId(command.assigneeId() != null ? command.assigneeId() : existing.assigneeId())
            .createdAt(existing.createdAt())
            .updatedAt(Instant.now())
            .dueDate(command.dueDate() != null ? command.dueDate() : existing.dueDate())
            .build();
        
        Task saved = dataAccess.save(updated);
        
        eventBus.publish(new TaskUpdatedEvent(saved.id(), saved.status().name()));
        
        return saved;
    }
    
    /**
     * Deletes a task.
     */
    @Transactional
    public void deleteTask(String id) {
        Task task = findById(id)
            .orElseThrow(() -> new TaskNotFoundException(id));
        
        dataAccess.delete(task);
        
        eventBus.publish(new TaskDeletedEvent(id));
    }
    
    private void validate(CreateTaskCommand command) {
        if (command.title() == null || command.title().isBlank()) {
            throw new ValidationException("Title is required");
        }
        if (command.title().length() > 200) {
            throw new ValidationException("Title must be 200 characters or less");
        }
    }
    
    private Map<String, Object> buildCriteria(TaskQuery query) {
        Map<String, Object> criteria = new HashMap<>();
        if (query.status() != null) {
            criteria.put("status", query.status());
        }
        if (query.priority() != null) {
            criteria.put("priority", query.priority());
        }
        if (query.assigneeId() != null) {
            criteria.put("assigneeId", query.assigneeId());
        }
        return criteria;
    }
}
```

### Controller

```java
// task-plugin-core/src/main/java/com/example/task/controller/TaskController.java
package com.example.task.controller;

import com.example.task.service.TaskService;
import com.example.task.shared.*;
import io.brix.platform.commons.Page;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    
    private final TaskService taskService;
    
    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }
    
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Task createTask(@Valid @RequestBody CreateTaskCommand command) {
        return taskService.createTask(command);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Task> getTask(@PathVariable String id) {
        return taskService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping
    public Page<Task> listTasks(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) String assigneeId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        TaskQuery query = new TaskQuery(status, priority, assigneeId, page, size);
        return taskService.findTasks(query);
    }
    
    @PutMapping("/{id}")
    public Task updateTask(
            @PathVariable String id,
            @Valid @RequestBody UpdateTaskCommand command) {
        return taskService.updateTask(id, command);
    }
    
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@PathVariable String id) {
        taskService.deleteTask(id);
    }
}
```

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
