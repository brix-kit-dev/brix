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

```bash
pnpm create @brix/brix plugin todo-plugin --template full
```

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

### 3.1 Domain Service

```java title="backend/core/service/TodoService.java"
package com.example.todo.core.service;

import io.brix.runtime.sdk.api.EventBusCapability;
import io.brix.runtime.sdk.api.StateStoreCapability;
import com.example.todo.shared.Todo;
import com.example.todo.shared.TodoCompletedEvent;
import org.springframework.stereotype.Service;

/**
 * TODO domain service.
 * 
 * <p>This service demonstrates capability-first development:
 * <ul>
 *   <li>Uses EventBusCapability instead of Kafka client</li>
 *   <li>Uses StateStoreCapability instead of Redis client</li>
 *   <li>No infrastructure imports allowed (enforced by Architecture Guard)</li>
 * </ul>
 */
@Service
public class TodoService {
    
    private final EventBusCapability eventBus;
    private final StateStoreCapability stateStore;
    
    public TodoService(
            EventBusCapability eventBus,
            StateStoreCapability stateStore) {
        this.eventBus = eventBus;
        this.stateStore = stateStore;
    }
    
    /**
     * Creates a new TODO item.
     *
     * @param title the TODO title
     * @return the created TODO
     */
    public Todo createTodo(String title) {
        Todo todo = new Todo(
            UUID.randomUUID().toString(),
            title,
            false,
            Instant.now().toString()
        );
        
        // Store using capability - not Redis directly
        stateStore.put("todo:" + todo.getId(), todo);
        
        return todo;
    }
    
    /**
     * Completes a TODO and publishes domain event.
     *
     * @param todoId the TODO ID to complete
     */
    public void completeTodo(String todoId) {
        Todo todo = stateStore.get("todo:" + todoId, Todo.class)
            .orElseThrow(() -> new TodoNotFoundException(todoId));
        
        todo.setCompleted(true);
        stateStore.put("todo:" + todoId, todo);
        
        // Publish domain event - capability handles routing
        eventBus.publish("todo.completed", new TodoCompletedEvent(
            todoId,
            Instant.now().toString()
        ));
    }
}
```

### 3.2 REST Controller

```java title="backend/server/controller/TodoController.java"
package com.example.todo.server.controller;

import com.example.todo.core.service.TodoService;
import com.example.todo.shared.CreateTodoRequest;
import com.example.todo.shared.Todo;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for TODO operations.
 * 
 * <p>This controller lives in the server module, which depends on core.
 * It only uses service interfaces, never infrastructure directly.
 */
@RestController
@RequestMapping("/api/todos")
public class TodoController {
    
    private final TodoService todoService;
    
    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }
    
    @PostMapping
    public Todo createTodo(@RequestBody CreateTodoRequest request) {
        return todoService.createTodo(request.getTitle());
    }
    
    @PatchMapping("/{id}/complete")
    public void completeTodo(@PathVariable String id) {
        todoService.completeTodo(id);
    }
}
```

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

The scaffold includes Architecture Guard tests:

```java title="backend/test/ArchitectureTest.java"
package com.example.todo;

import io.brix.architecture.guard.AdapterIsolationRule;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Architecture tests enforcing Brix design constraints.
 */
class ArchitectureTest extends AdapterIsolationRule {
    
    private static final String BASE_PACKAGE = "com.example.todo";
    
    @ArchTest
    static final ArchRule noKafkaInDomain = noInfrastructureImports()
        .because("Plugins must use EventBusCapability, not Kafka directly");
    
    @ArchTest
    static final ArchRule noRedisInDomain = noRedisImports()
        .because("Plugins must use StateStoreCapability, not Redis directly");
}
```

Run the tests:

```bash
mvn test
# All 13 red-line rules should pass
```

## Part 6: Run and Test

```bash
# Terminal 1: Backend
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=simple

# Terminal 2: Frontend
cd frontend/web
pnpm dev
```

The `simple` profile uses in-memory implementations - no Kafka/Redis needed!

## Summary

You've created a complete plugin following Brix architecture:

| Layer | What You Built |
|-------|----------------|
| **Shared** | Todo types, events |
| **Backend Core** | TodoService (uses capabilities) |
| **Backend Server** | REST controller |
| **Frontend Repository** | API calls via HttpCapability |
| **Frontend ViewModel** | useTodoList hook |
| **Frontend View** | TodoListPage component |

All 13 Architecture Guard rules pass, ensuring your plugin is:
- Infrastructure agnostic
- Portable between Standalone/Embedded modes
- Easily testable with mocks

## Next Steps

- [Event Model](../concepts/event-model) - Publish events across plugins
- [Testing Guide](../guides/testing) - Write effective tests
- [Deployment Guide](../guides/deployment) - Deploy to production
