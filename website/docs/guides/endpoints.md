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
1. Finds the route: method and path, with literal path segments before variables ([Routes](#routes)).
2. Starts the call's server span, in a new trace ([Traces](#traces)).
3. Builds the request: the body of a `POST`, `PUT` or `PATCH`, or the query parameters of any other method, together
   with the path variables. It checks them against the request schema. A request that breaks it never reaches your
   handler.
4. Checks the caller, the tenant context and the permissions the manifest names.
5. Takes one of the endpoint's permits, or answers 503 at once when all are taken ([Deadlines and
   limits](#deadlines-and-limits)).
6. On an endpoint that [replays its results](#repeating-a-call), checks the call's key.
7. Opens your plugin's unit of work: read-only for `GET` and `HEAD`, read-write otherwise, bound to the verified tenant.
   Its transaction ends at the call's deadline.
8. On such an endpoint, takes the key. A repeated call gets the first answer here, and your handler does not run.
9. Calls your handler and waits for its answer, no longer than the deadline.
10. Checks the answer against the response schema and the deadline once more, commits, and answers with the status
    the manifest declares.

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
        mode: required
```

- `response.status` is what a success answers with: `200` (the default), `201`, `202` or `204`.
- Put the request and response schemas in your api artifact, under `META-INF/brix/schemas/`. For a `GET` the request
  schema describes the query parameters and path variables.
- A typed handler's schemas must be on the plugin's class path; the plugin does not start otherwise.
- A published schema version never changes, an endpoint's no more than a query's. The module that ships the file
  pins it in `src/main/brix/contracts.api` and runs `ContractSourcesTest`, so editing it fails the build; publish a
  change as a new version (see [Queries](./queries.md#1-write-the-contract)).
- `idempotency.mode` is `required` for a write the caller may repeat safely ([Repeating a call](#repeating-a-call)),
  or `not-applicable`. A write that is `not-applicable` is idempotent by its business key, or creates a new resource
  each time. `optional` stops the plugin.

## Routes

A path is a list of segments. A variable takes a whole segment, such as `{orderId}`; its name is a letter followed by
letters, digits or `_`. `/orders/{id}.json` and a name used twice are refused.

The host refuses to start when two endpoints of one method are the same route (root BRX-RS-MANIFEST-023). They are
the same when they differ only in variable names, duplicate or trailing slashes, letter case or percent-encoding:
`/api/items/{id}`, `/API/Items/{key}/` and `/api/%69tems/{id}` are one route.

When several routes match a request, the one with a literal segment where the others have a variable, at the first
segment where they differ, answers. Of these two, `GET /api/v1/tenants/by-code/members` goes to the first:

```text
GET /api/v1/tenants/by-code/{code}
GET /api/v1/tenants/{tenantId}/members
```

A request matches literal segments exactly, after decoding; path variables arrive decoded. A request path with a
broken encoding or a `.` or `..` segment matches nothing.

Two prefixes are not yours:
- `/api/platform/**` belongs to the platform's operational modules; a plugin endpoint there stops the host.
- `/oauth2/**`, `/.well-known/**`, `/auth/**` and `/bff/**` belong to the identity protocol surface; no endpoint may take
  them.

## Repeating a call

A client that gets no answer cannot tell whether its request took effect. On an endpoint that declares
`idempotency.mode: required`, it sends the request again and the runtime makes that safe (root BRX-RS-MANIFEST-018).
`sample-orders` does this for placing an order:

```http
POST /api/sample-orders/orders
Idempotency-Key: 6f1c2a3e-order-1
```

- The first call takes the key in your unit of work, runs your handler, and keeps the status and the body with your
  writes. All of it commits together.
- A later call with the same key and the same content gets that answer again, with the header
  `Idempotent-Replayed: true`. Your handler does not run. The content is compared by a fingerprint of the request:
  members in another order, or a field left out instead of sent as null, are the same content.
- A call that fails keeps nothing, because everything rolls back. It can be tried again with the same key, and your
  handler runs again.
- A key belongs to the endpoint, the tenant and the caller. Other callers can use the same key and never meet.
- The runtime keeps a key for at least 24 hours, and until the cleaning has removed it a later call still finds it.
  `retentionPolicyRef` names how long: `idempotency-24h` is the default and the only name for now. Another name stops
  the plugin at startup.

| Code | Status | When |
|---|---|---|
| `endpoint.idempotency_key_required` | 400 | The call has no `Idempotency-Key`, or the key is not 1 to 128 visible ASCII characters |
| `endpoint.idempotency_in_progress` | 409 | Another call with the key has not finished. The runtime does not wait for it. Try again shortly. |
| `endpoint.idempotency_conflict` | 422 | An earlier call used the key with other content. Send new content with a new key. |

Your handler reads its key from `request.idempotency()`, for example to name the same key to another system.

The plugin does not start unless the endpoint meets these rules:
- It is a `POST`, `PUT`, `PATCH` or `DELETE` with a typed handler, and declares its request and response schemas. A `204`
  needs no response schema.
- The caller is authenticated (`authentication.mode: required`). An anonymous caller has no key of its own.
- The plugin declares `data.storageId` and requires `PersistenceCapability`. The platform creates
  `<storageId>_endpoint_idempotency` from its own migrations.
- The response holds no field classified `secret` or `credential`, directly or through a `$ref`. The runtime would keep
  it.

The runtime keeps at most 64 KiB of an answer. A larger one fails the call with `platform.unknown` and rolls it back,
so nothing takes effect without an answer to replay. It logs `event=endpoint.idempotency_answer_too_large`.

### Operational endpoints

A platform operational module owns no data, so its handler runs in no unit of work. The record goes with its one write
through an owner's internal contract (root BRX-RS-HOST-020), and both sides declare it:

```yaml
# platform-operational.yaml
idempotency:
  mode: required
  writeContract: brix.internal.sample-orders.administration
```

```yaml
# the owner's plugin-manifest.yaml
internalContracts:
  provides:
    - contractId: brix.internal.sample-orders.administration
      # contractType, contractVersion, providerId, owner
      replayableWrites:
        - adjustQuantity
```

`replayableWrites` names the contract's methods whose results the runtime may keep, so they must hold no secret or
credential. Each name must be exactly one method of the contract. The owner declares `data.storageId`, requires
`PersistenceCapability`, and gets `<storageId>_endpoint_idempotency`. The endpoint meets the rules above, and the
contract must be one its module requires.

- The write takes the key in the owner's unit of work and keeps the contract's result with the owner's writes. A write
  the owner refuses rolls back and leaves no key.
- Once the handler has answered, the runtime keeps the answer in a short transaction of the owner.
- A later call with the same key and content gets that answer before the handler, as on a plugin's endpoint.
- A call can stop after the owner committed: the platform audit fails, the handler answers after the deadline (504), or
  the host dies. It has no answer yet, so a later call runs the handler again. The contract returns the kept result, the
  owner writes nothing, and the rest of the handler finishes. The answer carries `Idempotent-Replayed: true`, and the
  runtime logs `event=endpoint.idempotency_resumed`.
- If the answer cannot be kept, it still goes back. The runtime logs `event=endpoint.idempotency_answer_not_kept`, and
  a later call goes on as above. An answer over 64 KiB is treated the same way, with
  `event=endpoint.idempotency_answer_too_large`. A contract's result over 64 KiB fails the call and rolls it back.
- A handler makes one such write per call. A second one fails the call with `platform.unknown`. A handler that answers
  without reaching its write logs `event=endpoint.idempotency_key_unused`, and nothing is kept.

The bootstrap entries of a fresh install are an operational module's too. Their caller is the short-lived
BOOTSTRAP_SETUP session, not a platform administrator:

```yaml
# platform-operational.yaml, POST /api/platform/bootstrap/create-first-admin
authentication:
  mode: required
  policyRef: bootstrap-setup
authorization:
  permissions:
    - bootstrap:create-first-admin
tenantContext:
  mode: forbidden
```

- The host verifies the session's token before the handler and names the session by its `jti`: that is the handler's
  `actorId()`. The handler hands it to the data owner that opened the session, which checks that it is the session it
  opened last, unused and unexpired.
- `bootstrap-setup` accepts the bootstrap session only, and every other policy refuses it, including an endpoint that
  names no `policyRef`. An operational module may declare it under `/api/platform/bootstrap/` with
  `tenantContext.mode: forbidden`; a plugin that declares it does not start.
- The session is the caller the keys belong to. `create-first-admin` replays its answer through the identity owner's
  contract: the same session and key get the first answer. The write spends the session, so another key under it
  writes nothing, and once the session has expired the runtime answers 401 before it would replay.

## Deadlines and limits

`timeout.policyRef` names the call's deadline (root BRX-RS-MANIFEST-018, RESILIENCE-001):

| Policy | Deadline |
|---|---|
| `endpoint-default` | The host's endpoint deadline, 30 seconds unless the host sets another |
| `interactive-read` | 10 seconds, for a read a person waits for |
| `interactive-write` | 15 seconds, for a write a person waits for |

The host's endpoint deadline also caps the other two. Another name stops the plugin at startup. At the deadline the
runtime sets the call's cancellation signal, rolls the unit of work back and answers 504; your plugin's transaction
ends then too, and so do its waits for a database connection. A handler that does its work synchronously and returns
after the deadline gets the same: the runtime checks the deadline once more just before the commit.

An operational module's handler runs in no unit of work. Its writes go through owners' internal contracts, each in the
owner's own transaction, which ends at the deadline too. Its 504 therefore leaves the outcome open: a repeat with the
same `Idempotency-Key` gets the kept result.

Each endpoint runs at most 64 calls at once unless the host sets another limit. One more is answered 503
`endpoint.rejected` at once, before your handler runs; other endpoints are not affected. Quotas per tenant or per
caller belong to the entry gateway, not to the runtime.

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
  your unit of work when it completes in time, and rolls back when it completes exceptionally or after the deadline.
- Long work checks `request.cancellation().isCancelled()`: the runtime sets it at the deadline.
- `request.headers()` has no `Authorization`, `Proxy-Authorization`, `traceparent`, `tracestate`, `baggage` or
  `x-trace-id`: the runtime has verified the caller and holds the trace. Read the caller from `actorId()`, the tenant
  from `tenantId()` and the trace id from `traceId()`. The one exception is the token of a `setup-token` entry, the
  platform's old setup that the hosted setup page replaces, which the handler hands to the token's data owner to verify
  and spend.

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

The body is `{"errorCode": ..., "message": ..., "traceId": ...}`: the wire code twice, and the id of the call's
trace, which support can look up (root BRX-RS-ERROR-013). Never a stack trace, SQL or another tenant's data. Declare
the category of every code a caller can cause.

The runtime's own answers:

| Code | Status | When |
|---|---|---|
| `endpoint.invalid_request` | 400 | The request breaks its schema. The body lists `violations`, each a `path` and a `rule`, never a value. |
| `endpoint.rejected` | 503 | The endpoint already runs as many calls as the host allows |
| `endpoint.deadline_exceeded` | 504 | The handler did not answer before the deadline, or answered after it; its unit of work rolls back |
| `endpoint.idempotency_key_required`, `endpoint.idempotency_in_progress`, `endpoint.idempotency_conflict` | 400, 409, 422 | See [Repeating a call](#repeating-a-call) |
| `platform.unknown` | 500 | Anything else, such as a response that breaks its schema or an unexpected exception. The cause is only in the log. |

## Traces

A caller over HTTP is an untrusted upstream, so each call starts a new trace (root BRX-RS-OTEL-002, OTEL-004). A
well-formed `traceparent` from the caller is linked to the call's server span, never its parent; `tracestate` and
`x-trace-id` are not used. The queries, commands and events your handler sends carry the call's trace, and so do the
log lines written during the call (`traceparent` and `traceId` in the logging context).

With the OpenTelemetry adapter, the server span is named after the method and the route template, such as
`GET /api/sample-orders/orders/{orderId}`. It carries `http.request.method`, `http.route`, `brix.endpoint.module`,
`brix.endpoint.id` and `http.response.status_code`; a 5xx answer marks it as an error.

## Host settings and meters

| Setting | Default |
|---|---|
| `brix.runtime-shell.endpoint-deadline-millis` | `30000`: the deadline of `endpoint-default`, and the cap on the others |
| `brix.runtime-shell.endpoints.interactive-read` | `PT10S` |
| `brix.runtime-shell.endpoints.interactive-write` | `PT15S` |
| `brix.runtime-shell.endpoints.concurrency` | `64` calls at once, for each endpoint |

A setting longer than the endpoint deadline stops the host.

| Meter | What |
|---|---|
| `brix.endpoint.calls` | Calls and how long the runtime took to answer, by `module`, `endpoint` and `outcome`: `ok`, `replayed`, `invalid`, `denied`, `not_found`, `conflict`, `rejected`, `unavailable`, `deadline_exceeded` or `failed` |
| `brix.endpoint.in_flight` | Calls whose handler is running, by `module` and `endpoint` |

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

`EndpointHandler<EndpointInvocation<Object>, R>` still works. Its body is the raw JSON, it may return `EndpointResponse`
and throw `EndpointHandlingException`, and the runtime checks neither its request nor its response. It runs
synchronously, so the runtime cannot stop waiting for it; its transaction still ends at the deadline, and a handler that
returns after it is rolled back and answered 504. In 4.0 `EndpointHandler` takes the shape of `TypedEndpointHandler`,
which then becomes its deprecated alias. A handler written against `TypedEndpointHandler` does not change.

A platform operational module's endpoints follow the same rules. Its `platform-operational.yaml` declares
`request.schemaRef`, `response.schemaRef` and `response.status` as a plugin manifest does. A `TypedEndpointHandler` is
held to them, and the module does not start without its schemas on the class path. A handler of the old shape ignores
them.
