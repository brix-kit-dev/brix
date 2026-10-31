---
id: external-services
title: Calling external services
sidebar_label: External services
sidebar_position: 6
---

# Calling external services

This chapter is for plugin authors. It shows how a plugin calls a service outside the platform, such as a payment
provider or a public key endpoint, with `ExternalHttpCapability`. A plugin uses no HTTP client library of its own.

Other plugins are not external services. A plugin reaches them by typed queries, commands and events; the runtime
refuses an external service whose address is this host (root BRX-RS-ERROR-014, A-11).

## Declaring a service

```yaml title="META-INF/brix/plugin-manifest.yaml"
capabilities:
  required:
    - id: io.runtime.sdk.capability.ExternalHttpCapability
      version: ">=3.2.0 <4.0.0"
externalServices:
  - id: google-oauth-jwks
    baseUrlRef: google-oauth-jwks
    timeoutPolicyRef: external-default
    retryPolicyRef: external-idempotent-retry
    concurrencyPolicyRef: external-standard
```

- `id` is the name your code calls the service by.
- `baseUrlRef` names the host's directory entry that gives the address. The host sets it:

  ```yaml title="host application.yml"
  brix:
    external-services:
      google-oauth-jwks:
        base-url: https://www.googleapis.com
  ```

  Under the `prod` and `production` profiles an address must be `https`. A path in the address is the prefix of
  every request path.
- `authPolicyRef` is `none` (the default) in this release: the runtime adds no credentials.

| Policy | Meaning |
|---|---|
| `external-default` | The host's external call timeout, 5 seconds unless the host sets another. A call made while handling an endpoint, a query, a command, an event or a task also ends at that call's deadline. |
| `external-no-retry` | A failed call is not sent again. |
| `external-idempotent-retry` | A `GET`, `HEAD`, `PUT` or `DELETE` that gets no answer, or answers 502, 503 or 504, is sent again: up to 3 attempts, 100 ms apart and doubling, while the deadline allows. |
| `external-standard` | Your plugin makes at most 16 calls to the service at once; one more fails at once. |

Another name stops the plugin at startup, and so does an address that resolves to this host.

## Making a call

```java
ExternalHttpCapability externalHttp = context.require(ExternalHttpCapability.class);

ExternalHttpResponse keys = externalHttp.service("google-oauth-jwks")
    .send(ExternalHttpRequest.get("/oauth2/v3/certs"));
if (keys.successful()) {
    parse(keys.text());
}
```

- A request names a path within the service, query parameters, headers and a body. It names no host, no `..`
  segment, and no `Authorization`, `Cookie`, `Host`, trace or connection header.
- An answer with any status is a response; read `status()`. Redirects are not followed: a 3xx comes back as it is.
- The call carries the trace of the call your plugin is handling.
- Inside a query's provider only `GET` and `HEAD` go out: a query has no side effects (root BRX-RS-MESSAGE-002).

A call that gets no answer throws `BrixExternalHttpException`:

| Code | When |
|---|---|
| `external_http.undeclared_service` | Your manifest does not declare the service, or the host's directory has no address for it |
| `external_http.target_forbidden` | The address resolves to this host, a loopback, link-local or multicast address, or a name the host lists as its own |
| `external_http.deadline_exceeded` | No answer before the deadline |
| `external_http.circuit_open` | Calls to the service failed so often that the runtime does not try for 30 seconds |
| `external_http.rejected` | Your plugin already makes as many calls to the service as the host allows |
| `external_http.failed` | The connection or the exchange failed, or the answer was larger than the host allows |

## Host settings and meters

| Setting | Default |
|---|---|
| `brix.external-services.<baseUrlRef>.base-url` | none: the service's calls fail until it is set |
| `brix.external-http.timeout` | `PT5S` |
| `brix.external-http.concurrency` | `16` calls at once, per plugin and service |
| `brix.external-http.attempts` | `3` |
| `brix.external-http.backoff` | `PT0.1S` |
| `brix.external-http.max-response-bytes` | `1048576` |
| `brix.external-http.self-addresses` | none: names or addresses the host is reached at from outside, such as its public address behind a proxy |

| Meter | What |
|---|---|
| `brix.external_http.calls` | Calls and how long they took, retries included, by `module`, `service` and `outcome` (`2xx` to `5xx`, or the failure) |

With the OpenTelemetry adapter, each attempt is a CLIENT span named after the method and the service, with the
server's address and port but not the path or the query.

## `HttpCapability`

`HttpCapability` takes any URL and is deprecated; it is removed in 4.0. Declare the service and use
`ExternalHttpCapability` instead.
