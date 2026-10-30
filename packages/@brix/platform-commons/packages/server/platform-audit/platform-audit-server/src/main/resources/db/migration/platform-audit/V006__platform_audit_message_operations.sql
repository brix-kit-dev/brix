-- Kafka design D33, §14 K14: an operator's replay, discard or republish of a message is written by the runtime
-- custodian runtime.message-operations and by nobody else, and each such row carries a trace id, a reason code and
-- its context (operator, reason, place), as the cross-tenant policy actions do since V002. Expand only: existing
-- rows have none of these actions.
ALTER TABLE sys_platform_audit_log ADD CONSTRAINT ck_platform_audit_message_writer
    CHECK ((action IN ('MESSAGE_REPLAYED', 'MESSAGE_DISCARDED', 'MESSAGE_REPUBLISHED'))
           = (writer = 'runtime.message-operations'));

ALTER TABLE sys_platform_audit_log ADD CONSTRAINT ck_platform_audit_message_fields
    CHECK (action NOT IN ('MESSAGE_REPLAYED', 'MESSAGE_DISCARDED', 'MESSAGE_REPUBLISHED')
           OR (trace_id IS NOT NULL AND reason_code IS NOT NULL AND context IS NOT NULL));
