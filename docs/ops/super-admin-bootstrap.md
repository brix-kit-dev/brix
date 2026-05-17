# Platform Super-Admin Bootstrap & Disaster-Recovery Runbook

Audience: platform operators / SRE on-call.
Companion: [`docs/v1.0-平台超管最小实现-唯一真相来源.md`](../v1.0-平台超管最小实现-唯一真相来源.md) (SSOT).

This runbook describes how to:

1. provision the very first `SUPER_ADMIN` on a new environment;
2. rotate the bootstrap password;
3. recover the platform when every interactive `SUPER_ADMIN` account is
   locked out (the "disaster" scenario).

---

## 1. Why a YAML bootstrap?

Per SSOT §4.1, the `SUPER_ADMIN` role is the only role with the
`platform:bypass` permission and the only role that can grant
`platform:admin.create` to others. We therefore need a way to create
that *first* admin **before** any other admin exists. The chosen design
is a one-shot YAML-driven bootstrap that runs at application start-up:

- The configuration block `brix.platform.bootstrap.super-admin` declares
  a single seed admin: username, e-mail, and an initial password (almost
  always sourced from a sealed secret / KMS — never committed).
- On boot, `PlatformAdminBootstrapService` runs **before** the REST
  layer is open for traffic. If a row matching the configured username
  already exists, the service is a no-op. Otherwise it creates the row
  with `forcePasswordChange = true`, so the operator is forced to rotate
  immediately on first login.
- The service is idempotent: re-running it (e.g. after a pod restart)
  does NOT reset an existing admin's password.

## 2. Provisioning a new environment

### 2.1 Configure the seed

Set these properties in the environment-specific Spring configuration
(typically via Kubernetes `Secret` mounted as env vars):

```yaml
brix:
  platform:
    bootstrap:
      super-admin:
        username: ops-bootstrap
        email: ops-bootstrap@example.com
        # MUST be sourced from a sealed secret. Never commit cleartext.
        password: ${BRIX_BOOTSTRAP_PASSWORD}
        display-name: "Platform Bootstrap"
```

Password requirements (enforced by `PasswordPolicy`):
- ≥ 12 characters
- at least one uppercase, one lowercase, one digit, one symbol.

### 2.2 Deploy

Deploy the platform as usual (`mvn -pl …platform-admin spring-boot:run`
or via your container image). Watch the boot log for one of:

```
PlatformAdminBootstrapService: bootstrap admin 'ops-bootstrap' created
PlatformAdminBootstrapService: bootstrap admin 'ops-bootstrap' already exists; skipping
```

### 2.3 First login

1. Open `/platform/login` and sign in with the bootstrap credentials.
2. The UI immediately redirects to the change-password page (because
   `forcePasswordChange === true`).
3. Pick a strong password and submit. The bootstrap password is now
   useless — the only way to authenticate as `ops-bootstrap` is with
   the new password.

### 2.4 Provision additional admins

From the Super-Admin dashboard:

1. Navigate to **Admins → Create**.
2. Choose `PLATFORM_ADMIN`, `SUPPORT_ADMIN`, or `AUDITOR`. (Note:
   `SUPER_ADMIN` is **not** offered — it is bootstrap-only by design.)
3. The dialog shows a one-shot temporary password. Copy it via the
   in-dialog Copy button and deliver it out-of-band (signed Slack DM,
   sealed envelope, etc.). The password is shown exactly once and the
   server only stores the bcrypt hash.
4. The new admin will be forced to change their password on first login.

### 2.5 Lock down the bootstrap

After enough humans hold accounts, **disable** `ops-bootstrap`:

1. Sign in as a different `SUPER_ADMIN`-or-`PLATFORM_ADMIN`-with-disable.
2. Disable `ops-bootstrap`, capturing a reason such as
   `"superseded by named admins after env launch"`.
3. The audit log captures `SUPER_ADMIN_DISABLED` with that reason.

## 3. Rotating the bootstrap password

For a planned rotation:

1. Update the sealed secret backing `BRIX_BOOTSTRAP_PASSWORD`.
2. Sign in as the bootstrap admin (with the **old** password).
3. Use the change-password flow to set the **new** password — this is
   the canonical rotation path because it's the same flow every other
   admin uses, ensuring consistent audit-log entries.
4. (Optional) restart the platform pods so the YAML-supplied value
   matches what's actually in the database. Because the bootstrap is
   idempotent, the YAML password is only used when the row does NOT
   exist, so a mismatch is harmless but confusing.

## 4. Disaster recovery — total lockout

Symptoms: every interactive `SUPER_ADMIN`/`PLATFORM_ADMIN` account is
disabled, has a forgotten password, or is otherwise unable to log in;
the platform is operating but no administrative actions can be taken.

Recovery steps (perform from a hardened jump-host with database access):

1. **Freeze the audit log perimeter.** Tag the runbook execution with a
   ticket ID; every step below MUST land in `platform_audit_log` after
   step 5. If the on-call cannot perform step 6, do not start step 1.
2. **Confirm the bootstrap row exists.**
   ```sql
   SELECT id, username, status, force_password_change
     FROM platform_admin
    WHERE username = '<bootstrap-username>';
   ```
3. **Re-enable the bootstrap row** (if disabled) and **clear** any
   account-lockout flags:
   ```sql
   UPDATE platform_admin
      SET status = 'ACTIVE',
          failed_login_count = 0,
          locked_until = NULL,
          force_password_change = TRUE
    WHERE username = '<bootstrap-username>';
   ```
4. **Reset its password hash** to the sealed-secret value. Generate a
   bcrypt hash off-cluster (`htpasswd -nbBC 12 '' <pw>` works), then:
   ```sql
   UPDATE platform_admin
      SET password_hash = '<bcrypt-hash>',
          updated_at = NOW()
    WHERE username = '<bootstrap-username>';
   ```
5. **Insert a manual audit-log row** capturing the recovery action
   (operator name, ticket, justification). Format:
   ```sql
   INSERT INTO platform_audit_log
     (action, actor_username, target_type, target_id, result, reason, created_at)
   VALUES
     ('PLATFORM_RECOVERY_PASSWORD_RESET', '<operator>', 'PlatformAdmin',
      '<bootstrap-row-id>', 'SUCCESS', '<ticket>: out-of-band recovery', NOW());
   ```
6. **Sign in as the bootstrap admin**, immediately rotate via the change
   -password flow, and create at least one additional `PLATFORM_ADMIN`.
7. **File a post-incident review.** Total lockout indicates the
   account-recovery design or the on-call rotation needs work — capture
   what failed in the SSRE backlog.

> ⚠️ Do NOT issue a temporary password by editing the database
> directly when normal admin paths are available. This recovery flow is
> the *last* resort precisely because it bypasses the audit-log
> guarantees enforced by the application layer.

## 5. References

- SSOT §4.1 — role hierarchy and bootstrap rules.
- SSOT §6 — REST API contract for the platform-admin module.
- SSOT §8.4 — temp-password lifecycle.
- [`packages/@brix/platform-commons/packages/server/platform-admin/CHANGELOG.md`](../../packages/@brix/platform-commons/packages/server/platform-admin/CHANGELOG.md)
