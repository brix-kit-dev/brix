-- Root BRX-RS-MESSAGE-026 / Kafka design §14 K19: bring platform-audit's canonical messaging tables to the
-- platform schema v1 (runtime-orchestrator META-INF/brix/messaging/postgresql, V1),
-- which the runtime checks at startup.

-- Inbox: an index for the cleanup of old deduplication records (K16).
CREATE INDEX idx_platform_audit_inbox_processed ON platform_audit_inbox (processed_at);
