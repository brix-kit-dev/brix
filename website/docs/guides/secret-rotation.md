# Secret Rotation Guide

This guide describes how to integrate secret rotation with the Brix platform
using **HashiCorp Vault** or **AWS Secrets Manager**. The goal is to eliminate
static credentials in configuration files and instead have secrets rotated
automatically on a configurable schedule.

---

## 1. Current State and Motivation

By default, Brix stores database passwords, JWT signing keys, and API tokens as
plain-text values in `application.yml` (or environment variables). This works for
development but introduces several production risks:

| Risk                          | Impact                                  |
| ----------------------------- | --------------------------------------- |
| Credential leak via VCS       | Compromised data access                 |
| Long-lived static passwords   | Wider blast radius on breach            |
| Manual rotation               | Human error, downtime during rotation   |
| No audit trail                | Hard to prove compliance (SOC 2, HIPAA) |

Adopting a secrets manager resolves all of the above by providing:
- **Automatic rotation** on a schedule (e.g., every 30 days)
- **Audit logging** of every secret access
- **Dynamic credentials** that expire after a TTL
- **Centralized access control** (policies, roles)

---

## 2. Architecture Overview

```
┌─────────────┐         ┌──────────────────┐
│ Brix Host   │◄────────│ Secrets Manager  │
│ (Spring Boot)│  lease  │ (Vault / AWS SM) │
│             │  renew   │                  │
└──────┬──────┘         └────────┬─────────┘
       │                         │
       │  JDBC connection        │  rotate credentials
       ▼                         ▼
┌─────────────┐         ┌──────────────────┐
│  Database   │◄────────│  Rotation Lambda │
│  (PostgreSQL)│         │  / Vault Plugin  │
└─────────────┘         └──────────────────┘
```

**Spring Cloud Vault** or **Spring Cloud AWS** acts as a property source that
transparently injects rotated secrets into the Spring `Environment` at startup
and refreshes them on lease renewal.

---

## 3. Option A — HashiCorp Vault Integration

### 3.1 Dependencies

Add to the **host** module's `pom.xml`:

```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-vault-config</artifactId>
</dependency>
<!-- For database dynamic credentials -->
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-vault-config-databases</artifactId>
</dependency>
```

### 3.2 Bootstrap Configuration

Create `bootstrap.yml` (or use `spring.config.import` in Spring Boot 3.2+):

```yaml
# Option 1: spring.config.import (recommended for Spring Boot 3.2+)
spring:
  config:
    import: vault://

  cloud:
    vault:
      uri: ${VAULT_ADDR:http://localhost:8200}
      authentication: TOKEN          # or APPROLE, KUBERNETES
      token: ${VAULT_TOKEN}
      kv:
        enabled: true
        backend: secret
        default-context: brix
      database:
        enabled: true
        role: brix-app               # Vault database role
        backend: database
```

### 3.3 Vault Server Setup (abbreviated)

```bash
# Enable the database secrets engine
vault secrets enable database

# Configure PostgreSQL connection
vault write database/config/brix-db \
  plugin_name=postgresql-database-plugin \
  allowed_roles="brix-app" \
  connection_url="postgresql://{{username}}:{{password}}@db-host:5432/brix" \
  username="vault_admin" \
  password="<admin-password>"

# Create a role with rotation TTL
vault write database/roles/brix-app \
  db_name=brix-db \
  creation_statements="CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}'; \
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO \"{{name}}\";" \
  default_ttl="1h" \
  max_ttl="24h"
```

### 3.4 How It Works at Runtime

1. On startup, Spring Cloud Vault requests a **dynamic database credential**
   from `database/creds/brix-app`.
2. Vault creates a short-lived PostgreSQL role and returns the username/password.
3. HikariCP connects using the dynamic credential.
4. Spring Cloud Vault **renews the lease** before expiry.
5. When the lease cannot be renewed (max TTL reached), a new credential is
   obtained and HikariCP's connection pool is refreshed.

### 3.5 KV Secrets for Non-Database Credentials

Store JWT signing keys, API tokens, and other secrets in Vault KV:

```bash
vault kv put secret/brix \
  jwt.signing-key="<base64-encoded-key>" \
  oauth2.client-secret="<client-secret>"
```

These are injected as Spring properties:
- `${jwt.signing-key}`
- `${oauth2.client-secret}`

---

## 4. Option B — AWS Secrets Manager Integration

### 4.1 Dependencies

```xml
<dependency>
  <groupId>io.awspring.cloud</groupId>
  <artifactId>spring-cloud-aws-starter-secrets-manager</artifactId>
</dependency>
```

### 4.2 Configuration

```yaml
spring:
  config:
    import: aws-secretsmanager:/brix/prod

  cloud:
    aws:
      secrets-manager:
        region: ${AWS_REGION:us-east-1}
        reload:
          strategy: refresh
          period: 60s        # poll interval for rotated secrets
```

### 4.3 Create and Configure Rotation in AWS

```bash
# Create the secret
aws secretsmanager create-secret \
  --name /brix/prod/database \
  --secret-string '{"username":"brix_app","password":"initial-password"}'

# Enable automatic rotation (every 30 days)
aws secretsmanager rotate-secret \
  --secret-id /brix/prod/database \
  --rotation-lambda-arn arn:aws:lambda:us-east-1:123456789:function:SecretsManagerRDSPostgreSQL \
  --rotation-rules '{"AutomaticallyAfterDays": 30}'
```

### 4.4 How It Works at Runtime

1. On startup, Spring Cloud AWS reads `/brix/prod` secrets and maps them to
   Spring properties (e.g., `spring.datasource.password`).
2. The reload strategy polls Secrets Manager every 60 s.
3. When AWS rotates the secret (via Lambda), the new value is picked up on the
   next poll cycle and the `Environment` is refreshed.
4. HikariCP detects the refreshed property via Spring Cloud's
   `RefreshScopeRefreshedEvent` and re-creates connections.

---

## 5. Brix-Specific Integration Points

### 5.1 DataSource Refresh

When a secret rotates, the HikariCP pool must be refreshed. The recommended
approach is to mark the `DataSource` bean as `@RefreshScope`:

```java
@Bean
@RefreshScope
@ConfigurationProperties("spring.datasource.hikari")
public HikariDataSource dataSource() {
    return new HikariDataSource();
}
```

On a `RefreshScopeRefreshedEvent`, Spring destroys and recreates the bean,
causing HikariCP to establish new connections with the rotated credentials.

### 5.2 Multi-Tenant Considerations

In a multi-tenant deployment each tenant data source should also participate in
secret rotation. The `TenantDataSourceManager` (if present) should:

1. Listen for `RefreshScopeRefreshedEvent`.
2. Iterate over active tenant pools and call `HikariDataSource.close()` on
   stale pools.
3. Let the lazy-init logic re-create pools on next access with new credentials.

### 5.3 JWT Signing Key Rotation

For JWT signing keys, use a **key-pair rotation** strategy:

1. Store two keys in the secrets manager: `current` and `previous`.
2. Sign new tokens with `current`.
3. Verify tokens against both keys (graceful transition).
4. On rotation: `previous ← current`, `current ← newly generated`.

This ensures tokens signed before rotation remain valid until they expire.

---

## 6. Operational Runbook

### 6.1 Emergency Secret Rotation (Vault)

```bash
# 1. Revoke all active leases for the compromised role
vault lease revoke -prefix database/creds/brix-app

# 2. Rotate the root credential
vault write -f database/rotate-root/brix-db

# 3. Restart application pods to obtain new credentials
kubectl rollout restart deployment/brix-host
```

### 6.2 Emergency Secret Rotation (AWS)

```bash
# 1. Force immediate rotation
aws secretsmanager rotate-secret \
  --secret-id /brix/prod/database \
  --rotate-immediately

# 2. Trigger application refresh (or restart pods)
kubectl rollout restart deployment/brix-host
```

### 6.3 Monitoring and Alerting

| Metric / Log                           | Source       | Alert Threshold          |
| -------------------------------------- | ------------ | ------------------------ |
| `vault.token.ttl`                      | Vault Agent  | < 30 min remaining       |
| `spring.cloud.vault.lease.renew.error` | App logs     | Any occurrence           |
| `aws.secretsmanager.rotation.failed`   | CloudWatch   | Any occurrence           |
| Secret last rotated date               | SM console   | > rotation period + 1 d  |

### 6.4 Pre-Flight Checklist

Before enabling secret rotation in production:

- [ ] Vault / Secrets Manager is deployed and reachable from the application network
- [ ] IAM / Vault policies grant **minimum required** permissions
- [ ] Database roles created by Vault are tested with the application
- [ ] Connection pool refresh verified (restart-free rotation)
- [ ] Monitoring and alerts configured for lease/rotation failures
- [ ] Runbook reviewed with the on-call team
- [ ] Tested emergency rotation procedure in staging environment

---

## 7. Summary of Configuration Properties

| Property                                        | Default       | Description                                |
| ----------------------------------------------- | ------------- | ------------------------------------------ |
| `spring.cloud.vault.uri`                        | —             | Vault server address                       |
| `spring.cloud.vault.authentication`             | `TOKEN`       | Auth method (TOKEN, APPROLE, KUBERNETES)   |
| `spring.cloud.vault.database.role`              | —             | Vault database role name                   |
| `spring.cloud.vault.database.default-ttl`       | `1h`          | Lease TTL for dynamic credentials          |
| `spring.cloud.aws.secrets-manager.region`       | —             | AWS region                                 |
| `spring.cloud.aws.secrets-manager.reload.period`| `60s`         | Poll interval for rotated secrets          |

---

## References

- [Spring Cloud Vault Reference](https://docs.spring.io/spring-cloud-vault/reference/)
- [Spring Cloud AWS Secrets Manager](https://docs.awspring.io/spring-cloud-aws/docs/current/reference/html/#secrets-manager)
- [HashiCorp Vault Database Secrets Engine](https://developer.hashicorp.com/vault/docs/secrets/databases)
- [AWS Secrets Manager Rotation](https://docs.aws.amazon.com/secretsmanager/latest/userguide/rotating-secrets.html)
- [OWASP Secrets Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html)
