/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa.custodial;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The test owner's mapping of the table it lets the policy audit writer insert into.
 */
@Entity
@Table(name = "test_policy_audit")
public class PolicyAuditRow {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "action")
    private String action;

    protected PolicyAuditRow() {
    }
}
