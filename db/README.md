# qa-commons-db

Postgres test-oracle helper: fail-loud, `PreparedStatement`-only DB
verification for state the API can't show (a queued row, its claim
columns). No dependency on `core` - usable standalone if you only need DB
assertions.

`PostgresDatabase` is generic and table-agnostic - it has no idea what a
"notification" is. Build your own thin, project-specific oracle on top of
it (see `template`'s `NotificationsOracle` for the pattern), the same way
`template`'s `NotificationsEndpoint` wraps `api`'s generic `Endpoint`.

## Configuration

`DbConfig.fromEnv()` reads:

| Env var           | Default            |
|-------------------|--------------------|
| `QA_DB_HOST`       | `localhost`        |
| `QA_DB_PORT`       | `5432`             |
| `QA_DB_NAME`       | `notificationdb`   |
| `QA_DB_USER`       | `postgres`         |
| `QA_DB_PASSWORD`   | `postgres`         |

The defaults match the target
[Notification-Microservice](https://github.com/UseYourActive/Notification-Microservice)'s
own `docker-compose.yaml`/`.env.example` Postgres defaults (see
`template/README.md` for how to start that service). Override with env vars
to point at a different host/port - e.g. if port `5432` is already taken on
your machine by something else (see "Known collision" below).

## Framework self-tests (no external service needed)

```
mvn -pl db test
```

Spins up a real, ephemeral `postgres:16-alpine` container via Testcontainers
(matching the target service's own image), exercises `PostgresDatabase`
against it, then tears it down - no `QA_DB_*` config or running service
required. Requires Docker running locally; if Docker isn't available, these
tests **skip** (not fail) via a `DockerClientFactory`-backed assumption, the
same idiom `ui`'s `PlaywrightExtension` uses for a missing Chromium install
- so `mvn clean verify` from a fresh clone stays green whether or not Docker
is installed.

## Live oracle proof (`template`)

`template`'s `NotificationOracleTest` (`@Tag("live")`) sends a real
notification via `NotificationsEndpoint`, then looks the row up via
`NotificationsOracle` against the *actual* running service's Postgres -
proving the intake persisted a row, not just that the API returned `202`.
Run it with the notification service up (see `template/README.md`):

```
mvn -pl template test -DrunLive=true
```

It asserts identity (`id`/`recipient`/`channel` match what was sent) and
that the claim columns (`locked_by`/`locked_at`/`attempts_count`, added by
the service's own `V1.0.1__Add_queue_claim_columns.sql` migration for its
durable-queue poller) are readable - deliberately **never** their values,
since the poller can claim the row at any point after intake and `status`
races it (the same lesson `template/README.md` already documents for the
API-only tests).

## Known collision: port 5432 already in use

If `mvn -pl template test -DrunLive=true` fails with `password
authentication failed for user "postgres"` even though the service's
`.env` credentials are right, check whether something *other* than the
service's own Postgres container is also listening on 5432 - a native,
non-Docker PostgreSQL install is a common culprit. Confirm with:

```
netstat -ano | findstr :5432      # Windows
```

If two processes show up, the fix is to remap the *published* port, not to
stop the other install:

1. In the service repo's `.env`, change `DB_PORT` to something free (e.g.
   `15432`).
2. `docker-compose up -d postgres` from the service repo - recreates just
   that container. The service itself is unaffected, since it reaches
   Postgres over the internal Docker network by service name, not the
   published port.
3. Point the oracle at the new port: `QA_DB_PORT=15432 mvn -pl template
   test -DrunLive=true`.

Verify you're actually hitting the service's own database (not just *a*
database) before trusting a result - e.g. query `flyway_schema_history` and
confirm the known migrations (`1.0.0`-`1.0.3`) are present.
