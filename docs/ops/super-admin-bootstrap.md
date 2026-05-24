# Platform Super-Admin Bootstrap and Recovery Runbook

Audience: platform operators and SRE on-call.

Companions:

- `docs/v2.0-平台超管功能最小实现-设计蓝图.md`
- `docs/v3.0.9-运行壳架构设计蓝图.md`

This runbook covers the v2.0 platform super-admin operating flows:

1. bootstrap-stage recovery before the first formal platform super-admin is active;
2. TOTP recovery for an existing formal platform super-admin;
3. setup-token reissue for initial setup and password reset.

## 1. Operating Principles

- The bootstrap identity is not a formal platform super-admin. It only exists to create the first formal platform super-admin.
- Bootstrap does not use a reusable password. It uses the dedicated bootstrap setup flow and a short-lived `BOOTSTRAP_SETUP` token.
- A formal platform super-admin becomes usable only after password setup and Google Authenticator compatible TOTP binding are both complete.
- Setup links are delivered only through the notification capability. Production HTTP responses must only expose delivery markers such as `setupLinkSent: true`.
- After the first formal `PLATFORM_SUPER_ADMIN` identity is active with TOTP enabled, bootstrap is permanently completed and must not be recreated by restart.
- Platform admin tokens are `scope=PLATFORM` and do not carry `tenant_id` in the normal platform session.

## 2. Fresh Environment Bootstrap

### 2.1 Configure the bootstrap identity

Set only non-secret bootstrap identity metadata in the environment-specific Spring configuration:

```yaml
brix:
  platform:
    bootstrap:
      super-admin:
        username: ops-bootstrap
        email: ops-bootstrap@example.com
        display-name: Platform Bootstrap
```

Do not configure a reusable bootstrap password. The bootstrap setup flow is deliberately passwordless.

### 2.2 Start the platform

On startup, `SuperAdminBootstrapRunner` checks `sys_bootstrap_state` and active formal admins:

- if bootstrap is completed, it returns without creating anything;
- if no active formal super-admin exists, it ensures a restricted `BOOTSTRAP` grant exists;
- the bootstrap grant only has `platform:bootstrap:read` and `platform:bootstrap:create-first-admin`.

### 2.3 Create the first formal admin

1. Open `/platform/bootstrap`.
2. Complete the bootstrap setup session.
3. Submit the first formal admin metadata.
4. Confirm the UI only reports that the setup link was sent.
5. The target admin opens the emailed `/platform/setup?token=...` link, sets a password, scans the TOTP QR payload, and confirms a 6-digit TOTP code.

Bootstrap completion happens only after the formal admin identity is `ACTIVE`, MFA is enabled, the platform-admin grant is `ACTIVE`, and the role is `PLATFORM_SUPER_ADMIN`.

## 3. Bootstrap Disaster Recovery

Use this path only before bootstrap completion. If `sys_bootstrap_state.completed_at` is already set, bootstrap must stay completed.

### 3.1 Confirm state

From a hardened database session:

```sql
SELECT completed_at, completed_by_identity_id
  FROM sys_bootstrap_state
 ORDER BY id
 LIMIT 1;

SELECT COUNT(*) AS active_formal_admins
  FROM sys_platform_admin pa
  JOIN sys_identity i ON i.id = pa.identity_id
 WHERE pa.role = 'PLATFORM_SUPER_ADMIN'
   AND pa.status = 'ACTIVE'
   AND i.status = 'ACTIVE'
   AND i.mfa_enabled = TRUE;
```

Proceed only when `completed_at IS NULL` and `active_formal_admins = 0`.

### 3.2 Restore bootstrap grant metadata

If the restricted bootstrap grant was accidentally disabled before completion, restore only the bootstrap identity and grant metadata:

```sql
UPDATE sys_identity
   SET status = 'PENDING_SETUP',
       mfa_enabled = FALSE,
       mfa_secret_encrypted = NULL,
       updated_at = NOW()
 WHERE email = '<bootstrap-email>';

UPDATE sys_platform_admin
   SET status = 'ACTIVE',
       role = 'BOOTSTRAP',
       revoked_at = NULL,
       revoked_by = NULL,
       revoke_reason = NULL,
       updated_at = NOW()
 WHERE identity_id = (
       SELECT id FROM sys_identity WHERE email = '<bootstrap-email>'
 );
```

Record the recovery ticket in the audit system immediately after service access is restored. Do not create a formal admin directly in the database unless the application is unavailable and an incident commander approves the break-glass action.

## 4. TOTP Disaster Recovery

Use the normal admin path when at least one other formal platform super-admin can log in.

1. Sign in through `/platform/login` with a different formal platform super-admin.
2. Open the target admin in `/platform/admins`.
3. Run reset password for the target admin.
4. Confirm the response only reports setup-link delivery.
5. The target admin completes `/platform/setup?token=...` and binds a fresh TOTP secret.

The reset path must set the target identity back to `PENDING_SETUP`, clear MFA enablement, invalidate previous setup links, invalidate old platform tokens, issue a new setup link, and write audit events for the reset and setup completion.

### 4.1 Emergency database assist

Use this only when all formal admins are locked out and the application reset endpoint is unavailable:

```sql
UPDATE sys_identity
   SET status = 'PENDING_SETUP',
       mfa_enabled = FALSE,
       mfa_secret_encrypted = NULL,
       mfa_bound_at = NULL,
       token_version = token_version + 1,
       updated_at = NOW()
 WHERE email = '<target-admin-email>';
```

After this database assist, restore application access and use the normal reset endpoint to send a new setup link. Insert a manual audit row with the incident ticket, operator, target identity, and reason.

## 5. Setup-Link Reissue

Use setup-link reissue when a link expired, was superseded, or the user never received email.

### 5.1 Formal admin reset path

Call the platform admin reset endpoint through the UI or API:

```http
POST /api/platform/admins/{id}/reset-password
Authorization: Bearer <PLATFORM token>
```

Expected production response shape:

```json
{
  "setupLinkSent": true
}
```

The service must invalidate previous setup links for the identity before issuing the new link.

### 5.2 Initial setup path

For a newly created formal admin, use:

```http
POST /api/platform/admins
Authorization: Bearer <PLATFORM token>
```

Expected production response shape:

```json
{
  "id": "<platform-admin-id>",
  "identityId": "<identity-id>",
  "setupLinkSent": true
}
```

Do not paste setup links into chat, issue trackers, audit reasons, or application logs. Local development may use the configured development notification channel to inspect delivery, but production responses must not contain setup credentials.

## 6. Audit Checklist

Every recovery action must leave an audit trail with:

- operator identity;
- target identity or platform-admin grant;
- action taken;
- ticket or incident reference;
- result;
- reason without credentials, setup links, TOTP codes, or MFA secrets.

Required action coverage includes setup-link issue and consumption, TOTP binding, admin creation, admin revoke, password reset, password change, tenant status change, bootstrap first-admin creation, and bootstrap deactivation.

## 7. Validation Commands

Run these checks before closing the incident:

```powershell
mvn -pl packages/@brix/platform-commons/packages/server/platform-admin test
mvn -pl packages/@brix/platform-commons/packages/server/platform-auth test
node scripts/check-banned-tokens.mjs
```

The platform admin response scan must pass under a production profile integration-test run, and no frontend-visible permissions claim may include `platform:bypass`.