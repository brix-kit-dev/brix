/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa.fixture;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnsafeClearRepository extends JpaRepository<OwnedNote, Long> {

    @Modifying(clearAutomatically = true)
    @Query("update OwnedNote n set n.text = :text")
    int renameAll(@Param("text") String text);
}
