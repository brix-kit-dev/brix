This directory is reserved for platform-identity Data Owner migrations.

Do not add an empty Flyway version. The first SQL migration must introduce the
actual Bootstrap/Identity schema when the ownership migration slice moves those
tables out of platform-tenant.
