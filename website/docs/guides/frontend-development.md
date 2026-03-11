---
id: frontend-development
title: Frontend Development Guide
sidebar_label: Frontend Development
sidebar_position: 2
---

# Frontend Development Guide

This guide covers building frontend components for Brix plugins using TypeScript and React.

## Project Structure

```
my-plugin-web/
├── src/
│   ├── index.ts              # Module entry
│   ├── routes.tsx            # Route definitions
│   ├── views/                # React components (View layer)
│   │   ├── OrderListPage.tsx
│   │   └── OrderDetailPage.tsx
│   ├── viewmodels/           # State & logic hooks (ViewModel layer)
│   │   ├── useOrders.ts
│   │   └── useOrderDetail.ts
│   ├── repositories/         # Data access (Repository layer)
│   │   └── OrderRepository.ts
│   └── shared/               # Shared types
│       └── types.ts
├── package.json
└── tsconfig.json
```

## Setup

### package.json

```json
{
  "name": "@myplugin/web",
  "version": "1.0.0",
  "type": "module",
  "main": "dist/index.js",
  "types": "dist/index.d.ts",
  "scripts": {
    "build": "tsc",
    "dev": "tsc --watch"
  },
  "dependencies": {
    "@brix/runtime-sdk-api-web": "workspace:*",
    "@brix/shared-runtime-web": "workspace:*"
  },
  "peerDependencies": {
    "react": "^18.0.0"
  }
}
```

### tsconfig.json

```json
{
  "extends": "../../tsconfig.base.json",
  "compilerOptions": {
    "outDir": "./dist",
    "rootDir": "./src",
    "declaration": true,
    "jsx": "react-jsx"
  },
  "include": ["src/**/*"]
}
```

## Importing React

**Always import from shared-runtime:**

```typescript
// ✅ CORRECT
import { React, useState, useEffect, useCallback } from '@brix/shared-runtime-web';

// ❌ WRONG - causes multiple React instances
import React from 'react';
import { useState } from 'react';
```

## View Layer

Views are pure presentational components. They:
- Render UI based on props and ViewModel data
- Dispatch events/actions to ViewModels
- Contain no business logic

```tsx
// src/views/OrderListPage.tsx
import { React } from '@brix/shared-runtime-web';
import { useOrders } from '../viewmodels/useOrders';
import { OrderTable } from './components/OrderTable';
import { LoadingSpinner } from '@brix/platform-commons-web';

export function OrderListPage() {
  const { orders, isLoading, error, refresh, deleteOrder } = useOrders();
  
  if (isLoading) {
    return <LoadingSpinner />;
  }
  
  if (error) {
    return (
      <div className="error-state">
        <p>Error loading orders: {error.message}</p>
        <button onClick={refresh}>Retry</button>
      </div>
    );
  }
  
  return (
    <div className="order-list-page">
      <header>
        <h1>Orders</h1>
        <button onClick={refresh}>Refresh</button>
      </header>
      
      <OrderTable 
        orders={orders} 
        onDelete={deleteOrder}
      />
    </div>
  );
}
```

### Component Guidelines

1. **Keep components small** - Extract reusable components
2. **Use TypeScript** - Type all props
3. **No API calls** - All data comes from ViewModels
4. **No business logic** - Use callbacks for actions

```tsx
// src/views/components/OrderTable.tsx
import { React } from '@brix/shared-runtime-web';
import type { Order } from '../../shared/types';

interface OrderTableProps {
  orders: Order[];
  onDelete: (id: string) => void;
}

export function OrderTable({ orders, onDelete }: OrderTableProps) {
  return (
    <table>
      <thead>
        <tr>
          <th>ID</th>
          <th>Customer</th>
          <th>Total</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>
        {orders.map(order => (
          <tr key={order.id}>
            <td>{order.id}</td>
            <td>{order.customerName}</td>
            <td>${order.total.toFixed(2)}</td>
            <td>
              <button onClick={() => onDelete(order.id)}>Delete</button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
```

## ViewModel Layer

ViewModels are custom hooks that encapsulate state and business logic.

```typescript
// src/viewmodels/useOrders.ts
import { useState, useEffect, useCallback } from '@brix/shared-runtime-web';
import { useOrderRepository } from '../repositories/OrderRepository';
import type { Order } from '../shared/types';

interface UseOrdersResult {
  orders: Order[];
  isLoading: boolean;
  error: Error | null;
  refresh: () => void;
  deleteOrder: (id: string) => Promise<void>;
}

export function useOrders(): UseOrdersResult {
  const [orders, setOrders] = useState<Order[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  
  const repository = useOrderRepository();
  
  const loadOrders = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    
    try {
      const data = await repository.findAll();
      setOrders(data);
    } catch (e) {
      setError(e instanceof Error ? e : new Error(String(e)));
    } finally {
      setIsLoading(false);
    }
  }, [repository]);
  
  const deleteOrder = useCallback(async (id: string) => {
    await repository.delete(id);
    setOrders(prev => prev.filter(o => o.id !== id));
  }, [repository]);
  
  useEffect(() => {
    loadOrders();
  }, [loadOrders]);
  
  return {
    orders,
    isLoading,
    error,
    refresh: loadOrders,
    deleteOrder,
  };
}
```

### ViewModel Patterns

#### Form ViewModel

```typescript
// src/viewmodels/useOrderForm.ts
import { useState, useCallback } from '@brix/shared-runtime-web';
import { useOrderRepository } from '../repositories/OrderRepository';
import type { CreateOrderRequest, Order } from '../shared/types';

interface UseOrderFormResult {
  formData: CreateOrderRequest;
  isSubmitting: boolean;
  errors: Record<string, string>;
  setField: (field: keyof CreateOrderRequest, value: unknown) => void;
  submit: () => Promise<Order | null>;
  reset: () => void;
}

const initialFormData: CreateOrderRequest = {
  customerId: '',
  items: [],
};

export function useOrderForm(): UseOrderFormResult {
  const [formData, setFormData] = useState<CreateOrderRequest>(initialFormData);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  
  const repository = useOrderRepository();
  
  const validate = useCallback((data: CreateOrderRequest): boolean => {
    const newErrors: Record<string, string> = {};
    
    if (!data.customerId) {
      newErrors.customerId = 'Customer is required';
    }
    if (data.items.length === 0) {
      newErrors.items = 'At least one item is required';
    }
    
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  }, []);
  
  const setField = useCallback((field: keyof CreateOrderRequest, value: unknown) => {
    setFormData(prev => ({ ...prev, [field]: value }));
    setErrors(prev => ({ ...prev, [field]: '' }));
  }, []);
  
  const submit = useCallback(async (): Promise<Order | null> => {
    if (!validate(formData)) {
      return null;
    }
    
    setIsSubmitting(true);
    try {
      const order = await repository.create(formData);
      return order;
    } catch (e) {
      setErrors({ _form: e instanceof Error ? e.message : 'Submission failed' });
      return null;
    } finally {
      setIsSubmitting(false);
    }
  }, [formData, repository, validate]);
  
  const reset = useCallback(() => {
    setFormData(initialFormData);
    setErrors({});
  }, []);
  
  return { formData, isSubmitting, errors, setField, submit, reset };
}
```

#### Paginated ViewModel

```typescript
// src/viewmodels/usePaginatedOrders.ts
import { useState, useEffect, useCallback } from '@brix/shared-runtime-web';
import { useOrderRepository } from '../repositories/OrderRepository';
import type { Order, PagedResult } from '../shared/types';

interface UsePaginatedOrdersResult {
  orders: Order[];
  page: number;
  pageSize: number;
  totalPages: number;
  isLoading: boolean;
  goToPage: (page: number) => void;
  nextPage: () => void;
  prevPage: () => void;
}

export function usePaginatedOrders(pageSize = 10): UsePaginatedOrdersResult {
  const [orders, setOrders] = useState<Order[]>([]);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  
  const repository = useOrderRepository();
  
  const loadPage = useCallback(async (pageNum: number) => {
    setIsLoading(true);
    try {
      const result: PagedResult<Order> = await repository.findPaged(pageNum, pageSize);
      setOrders(result.items);
      setTotalPages(result.totalPages);
    } finally {
      setIsLoading(false);
    }
  }, [repository, pageSize]);
  
  useEffect(() => {
    loadPage(page);
  }, [page, loadPage]);
  
  const goToPage = useCallback((p: number) => {
    if (p >= 1 && p <= totalPages) {
      setPage(p);
    }
  }, [totalPages]);
  
  const nextPage = useCallback(() => goToPage(page + 1), [page, goToPage]);
  const prevPage = useCallback(() => goToPage(page - 1), [page, goToPage]);
  
  return { orders, page, pageSize, totalPages, isLoading, goToPage, nextPage, prevPage };
}
```

## Repository Layer

Repositories encapsulate data access through capabilities.

```typescript
// src/repositories/OrderRepository.ts
import { useMemo } from '@brix/shared-runtime-web';
import { useCapability, HttpCapability } from '@brix/runtime-sdk-api-web';
import type { Order, CreateOrderRequest, PagedResult } from '../shared/types';

class OrderRepository {
  constructor(private http: HttpCapability) {}
  
  async findAll(): Promise<Order[]> {
    return this.http.get<Order[]>('/api/orders');
  }
  
  async findById(id: string): Promise<Order> {
    return this.http.get<Order>(`/api/orders/${id}`);
  }
  
  async findPaged(page: number, size: number): Promise<PagedResult<Order>> {
    return this.http.get<PagedResult<Order>>(`/api/orders?page=${page}&size=${size}`);
  }
  
  async create(request: CreateOrderRequest): Promise<Order> {
    return this.http.post<Order>('/api/orders', request);
  }
  
  async update(id: string, data: Partial<Order>): Promise<Order> {
    return this.http.put<Order>(`/api/orders/${id}`, data);
  }
  
  async delete(id: string): Promise<void> {
    return this.http.delete(`/api/orders/${id}`);
  }
}

export function useOrderRepository(): OrderRepository {
  const http = useCapability(HttpCapability);
  return useMemo(() => new OrderRepository(http), [http]);
}
```

## Event Handling

Subscribe to backend events using EventBusCapability:

```typescript
// src/viewmodels/useOrderEvents.ts
import { useEffect, useCallback } from '@brix/shared-runtime-web';
import { useCapability, EventBusCapability } from '@brix/runtime-sdk-api-web';
import type { OrderCreatedEvent, OrderUpdatedEvent } from '../shared/events';

type OrderEventHandler = {
  onOrderCreated?: (event: OrderCreatedEvent) => void;
  onOrderUpdated?: (event: OrderUpdatedEvent) => void;
};

export function useOrderEvents(handlers: OrderEventHandler) {
  const eventBus = useCapability(EventBusCapability);
  
  useEffect(() => {
    const subscriptions: Array<() => void> = [];
    
    if (handlers.onOrderCreated) {
      const sub = eventBus.subscribe<OrderCreatedEvent>(
        'order.created',
        handlers.onOrderCreated
      );
      subscriptions.push(() => eventBus.unsubscribe(sub));
    }
    
    if (handlers.onOrderUpdated) {
      const sub = eventBus.subscribe<OrderUpdatedEvent>(
        'order.updated',
        handlers.onOrderUpdated
      );
      subscriptions.push(() => eventBus.unsubscribe(sub));
    }
    
    // Cleanup subscriptions on unmount
    return () => {
      subscriptions.forEach(unsub => unsub());
    };
  }, [eventBus, handlers]);
}

// Usage in a component/ViewModel
function useRealTimeOrders() {
  const { orders, setOrders } = useOrderState();
  
  useOrderEvents({
    onOrderCreated: (event) => {
      setOrders(prev => [...prev, event.order]);
    },
    onOrderUpdated: (event) => {
      setOrders(prev => 
        prev.map(o => o.id === event.orderId ? { ...o, ...event.changes } : o)
      );
    },
  });
  
  return orders;
}
```

## Routing

Define routes for your plugin module:

```tsx
// src/routes.tsx
import { React, lazy, Suspense } from '@brix/shared-runtime-web';
import type { BrixRouteConfig } from '@brix/runtime-sdk-api-web';

// Lazy load pages for code splitting
const OrderListPage = lazy(() => import('./views/OrderListPage'));
const OrderDetailPage = lazy(() => import('./views/OrderDetailPage'));
const CreateOrderPage = lazy(() => import('./views/CreateOrderPage'));

export const orderRoutes: BrixRouteConfig[] = [
  {
    path: '/orders',
    element: (
      <Suspense fallback={<div>Loading...</div>}>
        <OrderListPage />
      </Suspense>
    ),
  },
  {
    path: '/orders/new',
    element: (
      <Suspense fallback={<div>Loading...</div>}>
        <CreateOrderPage />
      </Suspense>
    ),
  },
  {
    path: '/orders/:id',
    element: (
      <Suspense fallback={<div>Loading...</div>}>
        <OrderDetailPage />
      </Suspense>
    ),
  },
];
```

## Testing Frontend Code

### Testing ViewModels

```typescript
// src/viewmodels/__tests__/useOrders.test.ts
import { renderHook, act, waitFor } from '@testing-library/react';
import { useOrders } from '../useOrders';
import { mockCapability, MockHttpCapability } from '@brix/testing-utils-web';

describe('useOrders', () => {
  let mockHttp: MockHttpCapability;
  
  beforeEach(() => {
    mockHttp = mockCapability(HttpCapability);
  });
  
  it('should load orders on mount', async () => {
    const orders = [{ id: '1', customerName: 'Test' }];
    mockHttp.get.mockResolvedValue(orders);
    
    const { result } = renderHook(() => useOrders());
    
    expect(result.current.isLoading).toBe(true);
    
    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });
    
    expect(result.current.orders).toEqual(orders);
    expect(mockHttp.get).toHaveBeenCalledWith('/api/orders');
  });
  
  it('should handle delete', async () => {
    const orders = [
      { id: '1', customerName: 'Test1' },
      { id: '2', customerName: 'Test2' },
    ];
    mockHttp.get.mockResolvedValue(orders);
    mockHttp.delete.mockResolvedValue(undefined);
    
    const { result } = renderHook(() => useOrders());
    
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    
    await act(async () => {
      await result.current.deleteOrder('1');
    });
    
    expect(result.current.orders).toHaveLength(1);
    expect(result.current.orders[0].id).toBe('2');
  });
});
```

### Testing Components

```tsx
// src/views/__tests__/OrderTable.test.tsx
import { React } from '@brix/shared-runtime-web';
import { render, screen, fireEvent } from '@testing-library/react';
import { OrderTable } from '../components/OrderTable';

describe('OrderTable', () => {
  const mockOrders = [
    { id: '1', customerName: 'Alice', total: 100 },
    { id: '2', customerName: 'Bob', total: 200 },
  ];
  
  it('should render orders', () => {
    render(<OrderTable orders={mockOrders} onDelete={() => {}} />);
    
    expect(screen.getByText('Alice')).toBeInTheDocument();
    expect(screen.getByText('Bob')).toBeInTheDocument();
  });
  
  it('should call onDelete when delete button clicked', () => {
    const onDelete = jest.fn();
    render(<OrderTable orders={mockOrders} onDelete={onDelete} />);
    
    const deleteButtons = screen.getAllByText('Delete');
    fireEvent.click(deleteButtons[0]);
    
    expect(onDelete).toHaveBeenCalledWith('1');
  });
});
```

## Best Practices

1. **Import from shared-runtime** - Prevents multiple React instances
2. **Keep Views pure** - No data fetching or business logic
3. **Use TypeScript strictly** - Type all props, state, and returns
4. **Lazy load routes** - Better initial load performance
5. **Test ViewModels first** - They contain the logic
6. **Use Repository pattern** - Centralize API calls

## Next Steps

- [Backend Development](./backend-development) - Build complementary backend services
- [Testing Guide](./testing) - Complete testing strategies
- [Plugin Development](./plugin-development) - End-to-end plugin creation
