/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa.tenancy;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query form a plugin repository may declare (BRX-RS-DATA-024 item 3 and 4). */
public final class P0Repositories {

    private P0Repositories() {
    }

    public interface DocRepository extends JpaRepository<P0Doc, Long>, JpaSpecificationExecutor<P0Doc> {

        List<P0Doc> findByTitle(String title);

        long countByTitle(String title);

        boolean existsByTitle(String title);

        Page<P0Doc> findByTitleStartingWith(String prefix, Pageable pageable);

        List<TitleView> findViewsByTitleStartingWith(String prefix);

        @Query("select d from P0Doc d where d.title like :prefix")
        List<P0Doc> jpqlByTitlePrefix(@Param("prefix") String prefix);

        @Query("select count(d) from P0Doc d")
        long jpqlCountAll();

        @Query("select d.title from P0Doc d order by d.title")
        List<String> jpqlTitles();

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select d from P0Doc d where d.title like :prefix")
        List<P0Doc> lockByTitlePrefix(@Param("prefix") String prefix);

        @Query("select d from P0Doc d where d.id in (select n.doc.id from P0Note n)")
        List<P0Doc> withAnyNote();

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("update P0Doc d set d.title = :title")
        int bulkRetitle(@Param("title") String title);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("delete from P0Note n where n.text like :prefix")
        int bulkDeleteNotes(@Param("prefix") String prefix);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("update P0Doc d set d.tenantId = :tenant")
        int bulkMoveToTenant(@Param("tenant") Long tenant);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("insert into P0Note (id, tenantId, text) select d.id + 5000, :tenant, d.title from P0Doc d")
        int bulkCopyIntoTenant(@Param("tenant") Long tenant);
    }

    public interface NoteRepository extends JpaRepository<P0Note, Long> {

        @Query("select n from P0Note n join n.doc d where d.title like :prefix")
        List<P0Note> joinedByDocTitlePrefix(@Param("prefix") String prefix);

        @Query("select n from P0Note n join fetch n.doc")
        List<P0Note> fetchJoinedDocs();
    }

    public interface InviteRepository extends JpaRepository<P0Invite, Long> {

        Optional<P0Invite> findByTokenHash(String tokenHash);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select i from P0Invite i where i.tokenHash = :tokenHash")
        Optional<P0Invite> lockByTokenHash(@Param("tokenHash") String tokenHash);

        List<P0Invite> findByStatus(String status);
    }

    public interface GlobalRepository extends JpaRepository<P0Global, Long> {
    }

    public interface TitleView {
        String getTitle();
    }
}
