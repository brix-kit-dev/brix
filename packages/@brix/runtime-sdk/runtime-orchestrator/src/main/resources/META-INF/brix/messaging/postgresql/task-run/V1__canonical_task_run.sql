-- Brix canonical tables, platform schema v1 (root BRX-RS-MANIFEST-025; standard-link plan L6.4, D57): the task-run
-- table, for the reference DCP (PostgreSQL).
--
-- The platform applies this migration for every Owner whose manifest provides a task ({{storageId}}_task_run), with
-- {{storageId}} replaced by its data.storageId, in a schema history table of its own, after the Owner's own
-- migrations. The runtime checks the columns, keys and indexes at startup (CanonicalMessagingTables). Later platform
-- versions add migrations next to this one; an applied migration is never edited.

-- The runs of the Owner's tasks that claim their triggers (task-trigger): the host whose insert of
-- (task_id, trigger_id) commits first runs the trigger, and records how the run ended. The claim commits on its own,
-- before the run, so no lock is held while the task works. Rows hold no tenant: tasks run in the platform scope.
CREATE TABLE {{storageId}}_task_run (
    task_id             VARCHAR(128) NOT NULL,
    trigger_id          VARCHAR(64)  NOT NULL,
    scheduled_at        TIMESTAMPTZ  NOT NULL,
    claimed_by          VARCHAR(128) NOT NULL,
    claimed_at          TIMESTAMPTZ  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    attempts            INTEGER      NOT NULL,
    error_code          VARCHAR(64),
    finished_at         TIMESTAMPTZ,
    CONSTRAINT pk_{{storageId}}_task_run PRIMARY KEY (task_id, trigger_id),
    CONSTRAINT chk_{{storageId}}_task_run_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_{{storageId}}_task_run_finished ON {{storageId}}_task_run (finished_at);
