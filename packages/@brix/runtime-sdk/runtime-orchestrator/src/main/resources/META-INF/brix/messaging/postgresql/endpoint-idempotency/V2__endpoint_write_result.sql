-- Brix canonical messaging tables, platform schema v4 (root BRX-RS-HOST-020; idempotency design §5.6, D80): the result of
-- an operational endpoint's write.
--
-- An operational endpoint's handler runs in no transaction, so the record goes with its one write through an internal
-- contract: the owner's transaction takes the key and keeps the contract's result here. result_status and result_body
-- come later, with the endpoint's answer. A row with write_result and no result_status is a call that stopped after
-- the owner committed: a repeat runs the handler again, and the contract returns this result instead of writing again.
ALTER TABLE {{storageId}}_endpoint_idempotency ADD COLUMN write_result JSONB;
