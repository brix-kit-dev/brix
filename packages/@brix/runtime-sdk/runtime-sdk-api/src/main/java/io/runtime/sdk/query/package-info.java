/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

/**
 * Typed cross-plugin queries: the synchronous, read-only channel of root blueprint §6.2.
 *
 * <p>A providing plugin publishes the query's contract in its api artifact: a contract file and the request and
 * result schemas, from which the build generates the {@link io.runtime.sdk.query.QueryType} constant and the
 * contract types. A calling plugin declares the query under {@code queries.consumes} and calls it through the
 * {@link io.runtime.sdk.query.QueryClient} its {@link io.runtime.sdk.capability.QueryCapability} returns.</p>
 *
 * @since 3.3.0
 */
package io.runtime.sdk.query;
