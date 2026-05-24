-- =============================================================================
-- V019: Drop platform-admin v1 temporary-password schema artifacts
--
-- Rationale:
--   Super-admin v2.0 uses setup links + TOTP enrollment. Platform-admin
--   lifecycle state is represented by IdentityStatus.PENDING_SETUP and
--   setup-token rows. A temp-password expiry marker would keep the v1 model
--   alive in the final schema, even when no Java code reads it.
--
-- Production note:
--   Do not rewrite older Flyway migrations that may already be applied. This
--   forward migration converges both fresh and upgraded databases to the v2.0
--   schema.
-- =============================================================================

ALTER TABLE sys_platform_admin
    DROP COLUMN IF EXISTS temp_password_expires_at;
