/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa.fixture;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ForeignNoteRepository extends JpaRepository<ForeignNote, Long> {
}
