/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.capability;

import io.runtime.sdk.annotation.Since;

/**
 * Calls the services outside the platform that the plugin declares (root BRX-RS-ERROR-014, A-11; standard-link plan
 * L8.1, D62).
 *
 * <p>A plugin names each service it calls under {@code externalServices} in its manifest; the host's directory gives
 * the service's address ({@code brix.external-services.<baseUrlRef>.base-url}). A request names only a path relative
 * to that address, so a plugin cannot reach any other host. The runtime refuses an address that resolves to this
 * host: other plugins are reached by typed queries, commands and events, never over HTTP.</p>
 *
 * <p>The runtime gives each call its deadline (the earlier of the service's timeout policy and the deadline of the
 * call the plugin is handling), limits how many calls a plugin makes to a service at once, opens a circuit for a
 * service that keeps failing, retries idempotent methods as the retry policy allows, caps the size of the answer and
 * carries the trace.</p>
 *
 * <pre>{@code
 * ExternalHttpResponse keys = externalHttp.service("google-oauth-jwks")
 *     .send(ExternalHttpRequest.get("/oauth2/v3/certs"));
 * if (keys.successful()) {
 *     parse(keys.text());
 * }
 * }</pre>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
@Since("3.3.0")
public interface ExternalHttpCapability {

    /**
     * Returns the client of a service the plugin declares.
     *
     * @param serviceId the service's {@code externalServices[].id}
     * @return the client
     * @throws BrixExternalHttpException {@code external_http.undeclared_service} when the plugin does not declare the
     *     service or the host's directory has no address for it
     */
    ExternalService service(String serviceId);

    /**
     * The client of one external service.
     */
    interface ExternalService {

        /**
         * Returns the service's id.
         *
         * @return the {@code externalServices[].id}
         */
        String id();

        /**
         * Sends a request and waits for the answer.
         *
         * @param request the request
         * @return the answer, whatever its status
         * @throws BrixExternalHttpException {@code target_forbidden}, {@code deadline_exceeded}, {@code circuit_open},
         *     {@code rejected} or {@code failed} when the call gets no answer
         */
        ExternalHttpResponse send(ExternalHttpRequest request);
    }
}
