/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.tenant.support;

import java.util.function.Supplier;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.runtime.sdk.capability.UnitOfWork;

/**
 * Unit of work for JPA tests: joins the test's transaction or starts one, like the
 * persistence adapter does for a single owner.
 */
public final class TransactionTemplateUnitOfWork implements UnitOfWork {

    private final TransactionTemplate readWrite;
    private final TransactionTemplate readOnly;

    public TransactionTemplateUnitOfWork(PlatformTransactionManager transactionManager) {
        this.readWrite = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    @Override
    public <T> T required(Supplier<T> work) {
        return readWrite.execute(status -> work.get());
    }

    @Override
    public <T> T readOnly(Supplier<T> work) {
        return readOnly.execute(status -> work.get());
    }
}
