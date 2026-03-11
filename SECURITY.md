# Security Policy

## Supported Versions

The following table outlines the versions of Brix Platform that currently receive security updates:

| Version | Supported          |
| ------- | ------------------ |
| 3.x.x   | :white_check_mark: |
| < 3.0   | :x:                |

## Reporting a Vulnerability

We take security vulnerabilities seriously. If you discover a security issue, please follow these steps:

### Do NOT

- **Do NOT** create public GitHub issues for security vulnerabilities
- **Do NOT** disclose the vulnerability publicly until it has been addressed
- **Do NOT** exploit the vulnerability beyond what is necessary to demonstrate it

### Do

1. **Email us directly** at: brix.kit.dev@gmail.com
2. **Include the following information**:
   - Type of vulnerability (e.g., XSS, SQL injection, authentication bypass)
   - Full path(s) of the affected source file(s)
   - Step-by-step instructions to reproduce the issue
   - Proof-of-concept or exploit code (if possible)
   - Impact assessment (what could an attacker achieve?)

### What to Expect

- **Initial Response**: We will acknowledge receipt of your report within **48 hours**
- **Status Update**: We will provide a more detailed response within **7 days**, including:
  - Our assessment of the vulnerability
  - Expected timeline for a fix
  - Any questions we may have
- **Resolution**: We aim to resolve critical vulnerabilities within **30 days**

### Disclosure Policy

- We will coordinate with you on the disclosure timeline
- We will credit you in our security advisories (unless you prefer to remain anonymous)
- We may request an extension if the fix requires significant changes

## Security Best Practices for Users

When deploying Brix Platform, please follow these security recommendations:

### Configuration

- Always change default credentials
- Use strong, unique passwords for all service accounts
- Enable TLS/SSL for all network communications
- Restrict network access to management interfaces

### Infrastructure

- Keep all dependencies up to date
- Run services with minimal required privileges
- Use container security scanning in production
- Enable audit logging for security-relevant events

### Development

- Follow the principle of least privilege when implementing plugins
- Validate all user inputs
- Use parameterized queries for database operations
- Implement proper authentication and authorization

## Security Advisories

Security advisories will be published on:

- [GitHub Security Advisories](https://github.com/brix-kit-dev/brix/security/advisories)
- Project changelog with `[SECURITY]` prefix

## Contact

- **Security Team Email**: brix.kit.dev@gmail.com
- **Response Time**: Within 48 hours

---

Thank you for helping keep Brix Platform and its users safe!
