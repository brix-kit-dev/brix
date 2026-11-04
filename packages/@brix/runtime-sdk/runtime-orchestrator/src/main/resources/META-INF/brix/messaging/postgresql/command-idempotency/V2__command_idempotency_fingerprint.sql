-- Brix canonical messaging tables, platform schema v2 (root BRX-RS-MESSAGE-014, MESSAGE-026; standard-link plan
-- point 8): the request fingerprint of the command that holds each key, for the reference DCP (PostgreSQL).
--
-- The platform applies this migration after V1, for every Owner whose manifest provides a command. An applied
-- migration is never edited.

-- The SHA-256, in hex, of the canonical payload of the command that took the key. A later command with the same key
-- and the same fingerprint is a duplicate of it; with another fingerprint, a conflict. Rows written before this
-- migration have none and read as duplicates.
ALTER TABLE {{storageId}}_command_idempotency ADD COLUMN request_fingerprint VARCHAR(64);
