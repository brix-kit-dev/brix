/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.external;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.runtime.sdk.capability.ExternalHttpResponse;

/**
 * The default transport: the JDK's HTTP client (standard-link plan L8.4).
 *
 * <p>The timeout bounds the whole exchange, the body included, and the body stops being read at the size limit. It
 * never follows redirects: a 3xx comes back as an answer, so no redirect can lead past the target policy.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class JdkExternalHttpTransport implements ExternalHttpTransport {

    private static final Duration MAX_CONNECT = Duration.ofSeconds(5);

    private final HttpClient client;

    /**
     * Creates the transport.
     */
    public JdkExternalHttpTransport() {
        this(HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(MAX_CONNECT)
            .build());
    }

    JdkExternalHttpTransport(HttpClient client) {
        this.client = client;
    }

    @Override
    public ExternalHttpResponse send(String method, URI target, Map<String, List<String>> headers, byte[] body,
            Duration timeout, long maxResponseBytes) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(target)
            .timeout(timeout)
            .method(method, body.length == 0 ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body));
        headers.forEach((name, values) -> values.forEach(value -> request.header(name, value)));
        CompletableFuture<HttpResponse<byte[]>> exchange =
            client.sendAsync(request.build(), info -> new LimitedBody(maxResponseBytes));
        try {
            HttpResponse<byte[]> response = exchange.get(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
            return new ExternalHttpResponse(response.statusCode(), response.headers().map(), response.body());
        } catch (TimeoutException e) {
            exchange.cancel(true);
            throw new HttpTimeoutException("no answer within " + timeout);
        } catch (InterruptedException e) {
            exchange.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw cause instanceof IOException io ? io : new IOException(cause);
        }
    }

    /** Collects a body up to a size, and fails beyond it. */
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {

        private final long limit;
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        private long total;

        LimitedBody(long limit) {
            this.limit = limit;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            if (body.isDone()) {
                return;
            }
            for (ByteBuffer item : items) {
                total += item.remaining();
                if (total > limit) {
                    subscription.cancel();
                    body.completeExceptionally(new ResponseTooLargeException(limit));
                    return;
                }
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                bytes.write(chunk, 0, chunk.length);
            }
        }

        @Override
        public void onError(Throwable failure) {
            body.completeExceptionally(failure);
        }

        @Override
        public void onComplete() {
            body.complete(bytes.toByteArray());
        }
    }
}
