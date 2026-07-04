/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.auth.ticket;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for persistent context selection tickets.
 *
 * @since 3.2.2
 */
public interface ContextSelectionTicketRepository extends JpaRepository<ContextSelectionTicket, Long> {

    Optional<ContextSelectionTicket> findByTicketHash(String ticketHash);
}
