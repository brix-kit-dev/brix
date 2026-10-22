/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa.fixture;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OwnedNoteRepository extends JpaRepository<OwnedNote, Long> {

    Optional<OwnedNote> findByCode(String code);

    @Query("select n from OwnedNote n where n.text = :text")
    List<OwnedNote> findByText(@Param("text") String text);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OwnedNote n set n.text = :text where n.id = :id")
    int rename(@Param("id") Long id, @Param("text") String text);
}
