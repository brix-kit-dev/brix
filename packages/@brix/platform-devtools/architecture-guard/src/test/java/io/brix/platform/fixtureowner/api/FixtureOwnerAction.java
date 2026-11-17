/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.fixtureowner.api;

/**
 * A type of a Data Owner's published api: an operational module may use it.
 *
 * @param id the action's stable ID
 */
public record FixtureOwnerAction(String id) {
}
