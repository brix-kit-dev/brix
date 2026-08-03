/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.dataowner;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable registry that maps every governed table to exactly one Data Owner.
 */
public final class DataOwnerRegistry {

    private final Map<String, DataOwnerDefinition> ownersById;
    private final Map<String, DataOwnerDefinition> ownersByTable;

    private DataOwnerRegistry(
            Map<String, DataOwnerDefinition> ownersById,
            Map<String, DataOwnerDefinition> ownersByTable) {
        this.ownersById = Map.copyOf(ownersById);
        this.ownersByTable = Map.copyOf(ownersByTable);
    }

    /**
     * Builds a registry and rejects duplicate owner ids or duplicate table owners.
     *
     * @param owners owner definitions
     * @return immutable registry
     */
    public static DataOwnerRegistry of(Collection<DataOwnerDefinition> owners) {
        if (owners == null || owners.isEmpty()) {
            throw new IllegalArgumentException("Data owner registry must not be empty");
        }
        Map<String, DataOwnerDefinition> byId = new LinkedHashMap<>();
        Map<String, DataOwnerDefinition> byTable = new LinkedHashMap<>();
        for (DataOwnerDefinition owner : owners) {
            if (byId.putIfAbsent(owner.ownerId(), owner) != null) {
                throw new IllegalArgumentException("Duplicate Data Owner id: " + owner.ownerId());
            }
            for (String table : owner.tables()) {
                DataOwnerDefinition previous = byTable.putIfAbsent(table, owner);
                if (previous != null) {
                    throw new IllegalArgumentException(
                        "Table " + table + " is owned by both " + previous.ownerId() + " and " + owner.ownerId());
                }
            }
        }
        return new DataOwnerRegistry(byId, byTable);
    }

    /**
     * Looks up the owner of one table.
     *
     * @param tableName SQL table name
     * @return owning definition when registered
     */
    public Optional<DataOwnerDefinition> ownerOf(String tableName) {
        return Optional.ofNullable(ownersByTable.get(DataOwnerDefinition.normalizeTable(tableName)));
    }

    /**
     * Returns a registered owner by id.
     *
     * @param ownerId owner id
     * @return owner definition
     */
    public DataOwnerDefinition requireOwner(String ownerId) {
        DataOwnerDefinition owner = ownersById.get(ownerId);
        if (owner == null) {
            throw new IllegalArgumentException("Unknown Data Owner: " + ownerId);
        }
        return owner;
    }
}
