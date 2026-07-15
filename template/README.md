# qa-commons-template

Living example: qa-commons framework exercised against a real notification
service (create/send, list-failed, a validation-error path, and a
duplicate-payload path), proving both positive and negative flows through
the typed `ApiResult` - plus a DB test-oracle proof (see "DB oracle proof"
below) that verifies what the API response alone can't show.

## Run the notification service locally

The template's live tests target
[UseYourActive/Notification-Microservice](https://github.com/UseYourActive/Notification-Microservice),
a Quarkus service, run via Docker Compose:

1. Clone the service:
   ```
   git clone https://github.com/UseYourActive/Notification-Microservice
   ```
2. Copy `.env.example` to `.env` and fill in values. Real provider
   credentials (Telegram/Twilio/SendGrid) are **optional** for this test
   suite — the template tests assert the intake contract (202/400/response
   shape), not actual message delivery, so placeholder values are fine.
3. From the service repo, start it:
   ```
   docker-compose up -d --build
   ```
   The `--build` flag matters — without it Docker may reuse a stale cached
   image instead of the current `master`.
4. Verify it's up:
   ```
   curl http://localhost:8080/q/health/ready
   ```
   Both checks should report `UP`. Swagger UI is at
   `http://localhost:8080/q/swagger-ui` and the raw OpenAPI spec at
   `http://localhost:8080/q/openapi?format=json` if you want to explore the
   API directly.

## Run the tests

The live tests are tagged `@Tag("live")` and excluded by default — plain
`mvn test` (or `mvn clean verify` from the repo root) compiles and passes
with **zero** tests executed here, so a fresh clone never needs the service
running just to build.

With the notification service running (see above), run the live suite from
the qa-commons repo root:

```
mvn -pl template -am test -DrunLive=true
```

(`-am` builds `core`/`api` first since `template` depends on them and they
aren't installed to the local Maven repo.)

If the service isn't running, `QA_BASE_URL` defaults to
`http://localhost:8080`; override it with an env var to point at a
different host/port:

```
QA_BASE_URL=http://localhost:9090 mvn -pl template -am test -DrunLive=true
```

## DB oracle proof

`NotificationOracleTest` sends via `NotificationsEndpoint`, then looks the
row up directly in the service's Postgres via `NotificationsOracle`
(`qa-commons-db`) - proving the intake actually persisted a row, not just
that the API returned `202`. It asserts identity (id/recipient/channel) and
that the claim columns are *readable*, never their values (`status` races
the service's poller - see `db/README.md`).

Connection defaults (`QA_DB_HOST`/`QA_DB_PORT`/`QA_DB_NAME`/`QA_DB_USER`/
`QA_DB_PASSWORD`) match the service's own `docker-compose`/`.env.example`
Postgres defaults, so no extra config is needed beyond starting the service
as above. If port `5432` collides with something else already running on
your machine, see `db/README.md`'s "Known collision" section - override
with e.g. `QA_DB_PORT=15432 mvn -pl template test -DrunLive=true`.
