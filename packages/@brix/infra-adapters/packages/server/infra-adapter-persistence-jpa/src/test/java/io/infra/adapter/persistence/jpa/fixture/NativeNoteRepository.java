/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa.fixture;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NativeNoteRepository extends JpaRepository<OwnedNote, Long> {

    @Query(value = "select * from owned_note", nativeQuery = true)
    List<OwnedNote> everything();
}
