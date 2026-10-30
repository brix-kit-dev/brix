/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The orders, reached only through {@code PersistenceCapability.repository(...)} inside the plugin's unit of work.
 *
 * @author Brix Platform Authors
 * @since 3.2.0
 */
public interface SampleOrderRepository extends JpaRepository<SampleOrder, Long> {
}
