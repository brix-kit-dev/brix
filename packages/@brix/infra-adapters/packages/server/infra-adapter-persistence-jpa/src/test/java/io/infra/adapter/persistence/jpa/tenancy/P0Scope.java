/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa.tenancy;

import java.util.Set;

/** P0 stand-in for the tenant set the runtime binds to a unit of work. */
final class P0Scope {

    private static final ThreadLocal<Set<Long>> TENANTS = new ThreadLocal<>();

    private P0Scope() {
    }

    static void enter(Set<Long> tenants) {
        TENANTS.set(Set.copyOf(tenants));
    }

    static void exit() {
        TENANTS.remove();
    }

    static Set<Long> current() {
        return TENANTS.get();
    }
}
