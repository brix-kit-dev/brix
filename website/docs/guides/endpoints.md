---
id: endpoints
title: Endpoints
sidebar_label: Endpoints
sidebar_position: 5
---

# Endpoints

This chapter is for plugin authors. It shows how an HTTP endpoint is declared, how its handler is written, and what
the runtime's dispatcher does before and after the handler runs. A plugin writes no Spring MVC code: no controller,
no request mapping, no filter.

The samples `sample-orders` and `sample-shipping` use every part of it. They run end to end in `sample-e2e`.

## What the dispatcher does

For each request, in this order (root BRX-RS-MANIFEST-022):
1. Finds the route: method and path, with literal path segments before variables.
2. Builds the request: the body of a `POST`, `PUT` or `PATCH`, or the query parameters of any other method, together
   with the path variables. It checks them against the request schema. A request that breaks it never reaches your
   handler.
3. Checks the caller, the tenant context and the permissions the manifest names.
4. Opens your plugin's unit of work: read-only for `GET` and `HEAD`, read-write otherwise, bound to the verified tenant.
5. Calls your handler and waits for its answer, no longer than the deadline.
6. Checks the answer against the response schema, commits, and answers with the status the manifest declares.

## Declaring an endpoint

```yaml title="sample-orders-server: META-INF/brix/plugin-manifest.yaml"
endpoints:
  provides:
    - endpointId: sample-orders.place.v1
      transport: http
      method: POST
      path: /api/sample-orders/orders
      request:
        schemaRef: brix://schemas/sample-orders/place-order-request/1.0.0
      response:
        schemaRef: brix://schemas/sample-orders/placed-order/1.0.0
        status: 201
      handlerId: sample-orders.place.v1
      authentication:
        mode: required
        policyRef: tenant-actor
      authorization:
        permissions:
          - sample-orders:order:place
        decision: all
        resourcePolicyRef: sample-orders-place
      tenantContext:
        mode: required
        source: runtime
      timeout:
        policyRef: endpoint-default
      idempotency:
        mode: not-applicable
```

- `response.status` is what a success answers with: `200` (the default), `201`, `202` or `204`.
- Put the request and response schemas in your api artifact, under `META-INF/brix/schemas/`. For a `GET` the request
  schema describes the query parameters and path variables.
- A typed handler's schemas must be on the plugin's class path; the plugin does not start otherwise.
- `idempotency.mode` is `not-applicable` in this release. `required` and `optional` need the runtime to replay
  results, which it does not do yet, so they stop the plugin. Make a write endpoint idempotent by its business key,
  or let it create a new resource each time, as placing an order does.

## Writing the handler

A handler implements `TypedEndpointHandler<B, R>`: it takes the request as `B` and answers with `R`.

```java title="sample-orders-server: PlaceOrder.java"
final class PlaceOrder implements TypedEndpointHandler<PlaceOrderRequest, PlacedOrder> {

    private final OrderPlacement orders;

    PlaceOrder(OrderPlacement orders) {
        this.orders = Objects.requireNonNull(orders, "orders must not be null");
    }

    @Override
    public CompletionStage<PlacedOrder> handle(EndpointInvocation<PlaceOrderRequest> request) {
        long tenantId = Long.parseLong(request.tenantId().orElseThrow());
        try {
            SampleOrder order = orders.place(tenantId, request.body());
            return CompletableFuture.completedFuture(new PlacedOrder(String.valueOf(order.getId()), order.version()));
        } catch (InvalidOrderException invalid) {
            throw new SampleOrdersException(SampleOrdersException.Code.ORDER_INVALID);
        }
    }
}
```

- `B` and `R` are records in your api artifact, as the schemas describe them. A path variable is a property of `B`:
  `ShowOrder` takes `FindOrderRequest(orderId)` for `GET /api/sample-orders/orders/{orderId}`.
- Query parameters and path variables arrive typed as the request schema's properties say: integers, numbers,
  booleans and arrays of them. A query parameter whose schema property is an array may repeat.
- Name `B` in the `implements` clause. A lambda handler gets the request as a `JsonNode`.
- The tenant and the caller are the ones the runtime verified: `tenantId()`, `actorId()`, `access()`. Never take them
  from the request.
- Do the work synchronously and return a completed stage, or return a stage that completes later. The runtime commits
  your unit of work when it completes, and rolls back when it completes exceptionally.
- Long work checks `request.cancellation().isCancelled()`: the runtime sets it at the deadline.

### Failures

A handler never chooses an HTTP status. It throws a `BrixException` whose code names its category, and the runtime
answers with the category's status:

```java title="sample-orders-server: SampleOrdersException.java (excerpt)"
enum Code implements ErrorCode {
    ORDER_INVALID("sample-orders.order_invalid", ErrorCategory.INVALID),
    ORDER_NOT_FOUND("sample-orders.order_not_found", ErrorCategory.NOT_FOUND);
    // wireCode() and category() return the two values
}
```

| `ErrorCategory` | Status |
|---|---|
| `INVALID` | 400 |
| `DENIED` | 403 |
| `NOT_FOUND` | 404 |
| `CONFLICT` | 409 |
| `UNAVAILABLE` | 503 |
| `DEADLINE_EXCEEDED` | 504 |
| `INTERNAL` (a code that names no category) | 500 |

The body is `{"errorCode": ..., "message": ...}`, both the wire code: never a stack trace, SQL or another tenant's
data. Declare the category of every code a caller can cause.

The runtime's own answers:

| Code | Status | When |
|---|---|---|
| `endpoint.invalid_request` | 400 | The request breaks its schema. The body lists `violations`, each a `path` and a `rule`, never a value. |
| `endpoint.deadline_exceeded` | 504 | The handler did not answer before the deadline; its unit of work rolls back |
| `platform.unknown` | 500 | Anything else, such as a response that breaks its schema or an unexpected exception. The cause is only in the log. |

## Lists

A list endpoint takes `page` (from 1), `size` (1 to 100) and `sort` (`<property>` or `<property>,desc`) as query
parameters, and answers `{items, page, size, total}`:

```json title="list-orders-request (excerpt)"
"page": { "type": "integer", "minimum": 1 },
"size": { "type": "integer", "minimum": 1, "maximum": 100 },
"sort": { "type": "array", "maxItems": 2, "items": { "type": "string", "enum": ["placedAt", "placedAt,desc"] } }
```

```java
PageRequest page = PageRequest.of(request.body().page(), request.body().size(), request.body().sort());
List<OrderSummary> items = orders.list(tenantId, page.offset(), page.size(), page.sort());
return CompletableFuture.completedFuture(Page.of(page, items, orders.count(tenantId)));
```

The schema's `enum` lists the sort orders your query supports, so a caller cannot sort by a column you did not index.
`PageRequest` and `Page` are in `io.runtime.sdk.page`.

## Testing

| Level | How |
|---|---|
| Handler | A plain unit test: build an `EndpointInvocation` with the typed body and call `handle`. |
| Contract | The dispatcher checks requests and responses against the schemas. The end-to-end test covers a request that breaks one. |
| End to end | Start your plugins in a host made of configuration, as `sample-e2e` does, and call the endpoints over HTTP. |

## The handlers written before 3.3.0

`EndpointHandler<EndpointInvocation<Object>, R>` still works. Its body is the raw JSON, it may return
`EndpointResponse` and throw `EndpointHandlingException`, and the runtime checks neither its request nor its
response. In 4.0 `EndpointHandler` takes the shape of `TypedEndpointHandler`, which then becomes its deprecated
alias. A handler written against `TypedEndpointHandler` does not change.
