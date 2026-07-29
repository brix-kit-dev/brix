-- =============================================================================
-- V025: Phase 2 Bootstrap Anchor clean initialization
--
-- Runtime Shell 3.0.10 clean-base Phase 2 requires Bootstrap Stage A to be
-- reachable only through Runtime-published platform-admin endpoints. This
-- migration initializes only the singleton state row needed by the Owner
-- internal contract. It must not create sys_identity or sys_platform_admin rows.
-- =============================================================================

INSERT INTO sys_bootstrap_state (id)
VALUES (1)
ON CONFLICT (id) DO NOTHING;

COMMENT ON TABLE sys_bootstrap_state IS
    'Singleton bootstrap Stage A/B state. Row id=1 is verifier state only; no database Bootstrap user is seeded.';
