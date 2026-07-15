# supporting-cast v1

## Problem

Two settled-but-unbuilt pieces of the framework, per the architecture skill's
deferred-domain references:

- **DB test oracles** (`references/db-test-oracles.md`): the API often can't
  show what actually happened (a queued notification's row, its claim state).
  qa-commons has no way to verify DB state at all today, and the old
  framework's `SQLDatabaseHelper` set the wrong precedent twice over
  (swallowed exceptions, string-concatenated SQL) — this rebuild fixes both.
- **Allure reporting** (`references/reporting-allure.md`): attachments exist
  (`api`'s per-instance `AllureRestAssured` filter) or partially exist (`ui`
  saves screenshots/traces to disk but never attaches them to a report), but
  nothing ever turns `allure-results` into an openable HTML report, and
  nothing attaches the seed/baseUrl every live test needs for reproducibility.
  `qa-commons/plan.md`'s v1 Risks section explicitly deferred report
  generation ("not part of 'done' for any task") — this is that follow-up.

Verified against the live target service
([UseYourActive/Notification-Microservice](https://github.com/UseYourActive/Notification-Microservice))
before designing the oracle: its `notifications` table (via
`V1.0.0__Init_Schema.sql` + `V1.0.1__Add_queue_claim_columns.sql` +
`V1.0.2__Add_locale_and_message_to_notifications.sql`) is

```sql
CREATE TABLE notifications (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    recipient VARCHAR(255) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    template_name VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    payload JSONB,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    locked_by VARCHAR(255),      -- claim column, added V1.0.1
    locked_at TIMESTAMP,         -- claim column, added V1.0.1
    attempts_count INT NOT NULL DEFAULT 0,
    locale VARCHAR(50),
    message TEXT
);
```

`V1.0.1`'s own migration comment confirms the "queue" framing: "the
notifications table becomes the queue itself, claimed by a poller instead of
an in-memory channel" — i.e. `locked_by`/`locked_at` are exactly the "claim
columns" the mission means, and `status` is the column that races the poller
(matches the Q1 lesson: never assert on it).

Its `docker-compose.yaml` runs `postgres:16-alpine`, publishing
`${DB_PORT:-5432}:5432`, with `.env.example` defaults
`QUARKUS_DATASOURCE_DB=notificationdb`, `QUARKUS_DATASOURCE_USERNAME=postgres`,
`QUARKUS_DATASOURCE_PASSWORD=postgres`, `DB_HOST=localhost` — these become our
oracle's own env-config defaults (§ Design). `postgres`/`postgres` is the
upstream OSS service's own published local-dev default, the same class of
value as `QaConfig`'s hardcoded `localhost` `baseUrl` default — not a real
credential.

## Goal / Non-goals

Goals:
1. A new `db` module (`qa-commons-db`) with a generic, engine-scoped Postgres
   oracle primitive: fail-loud, `PreparedStatement`-only, typed
   records/`Optional`, per-instance env config, try-with-resources.
2. Framework self-tests for the oracle via Testcontainers Postgres — no
   external service needed, gracefully skipped if Docker isn't available.
3. A `template`-module proof: send via the existing `NotificationsEndpoint`,
   then assert row existence + claim-column readability directly against the
   live service's Postgres. `@Tag("live")`, identity/existence assertions
   only.
4. A small `Reporter` abstraction in `core` (interface + zero-dependency
   SLF4J default + a reflective, optional Allure bridge) so Allure stays
   opt-in per module — no compile-time Allure dependency ever enters `core`.
5. `ui`'s existing screenshot/trace capture (currently disk-only) additionally
   flows through `Reporter`, so it lands in the Allure report when Allure is
   present on `ui`'s own test classpath (test-scope only — never forced on
   `qa-commons-ui` consumers).
6. Real Allure HTML report generation via the `allure-maven` plugin, wired so
   one documented command turns an already-run live suite into one combined,
   openable report containing both `ui`'s screenshots and `template`'s API
   request/response pairs.
7. Every live test gets its datafaker seed + target base URL attached as
   report parameters, via the same `Reporter` abstraction.
8. README coverage: running/reading the report, the `db` module's setup.

Non-goals:
- No Mongo oracle (Postgres only — this mission's actual target; Mongo stays
  a documented future engine per the reference, not built speculatively).
- No write/seed/cleanup helpers on the oracle — read-mostly per the
  reference; the one write path (`NotificationsEndpoint.send`) already exists
  in `api`.
- No change to `api`'s or `template`'s existing (mandatory, direct) Allure
  dependency — that's explicitly unchanged, existing-surface-stays-unchanged
  territory. The new `Reporter` optionality is for the *new* work (`ui`
  attachments, cross-cutting seed/baseUrl parameters), not a retrofit.
- No Allure step/`@Step` listener wiring — not asked for by this mission's
  scope, and `template`/`api` can already use `@Step`/`Allure.step(...)`
  directly wherever they already hard-depend on Allure.
- No CI wiring for report publishing — local `mvn`/artifact viewing only,
  matching every other module's "local Maven execution only" scope so far.

## Design

### 1. `db` module — where it lives (the design question)

**Recommendation: a new `qa-commons-db` module, not an optional/provided-scope
dependency inside `core`.**

Reasoning:
- This is exactly the precedent the repo already established twice: `core`
  stays free of RestAssured/Allure "so it can be reused by future non-API
  modules," and `ui`/`perf` were added as their own modules "only when a real
  need arrives" rather than folded into `core` behind scope tricks. JDBC for
  DB oracles is that same kind of need.
- `<optional>true</optional>` (or `<scope>provided</scope>`) on a JDBC driver
  declared *inside* `core` still puts the driver on `core`'s own
  compile/test classpath and in `core`'s own built jar's dependency
  metadata — every `core` consumer who isn't scrupulously reading
  `optional`/`provided` semantics (or whose build tool doesn't honor them,
  e.g. some shading setups) can still end up carrying it. A separate artifact
  makes the boundary structural, not a flag someone can miss: a consumer who
  never adds `qa-commons-db` never sees `postgresql` or `testcontainers` on
  any classpath, full stop.
- It mirrors the existing module-boundary pattern exactly:
  `api`/`ui`/`perf` each pull in only what their own concern needs, all
  depending on `core` (or nothing) for the shared primitives. `db` fits the
  same slot.

`db` does **not** depend on `core` — nothing in a generic Postgres oracle
needs `QaConfig`/`JsonMapperFactory`/`SeededFaker`, and keeping it
dependency-free (beyond `slf4j-api`) means a consumer who wants DB assertions
without the rest of the framework can take `qa-commons-db` alone.

`db/pom.xml`:
- `org.postgresql:postgresql` (42.7.13) — main scope.
- `org.slf4j:slf4j-api` — main scope (logging, matches every other module).
- `org.testcontainers:testcontainers-bom` (1.21.4, import) +
  `org.testcontainers:postgresql` — test scope only.
- `junit-jupiter`, `assertj-core` — test scope.

### 2. `db` — `DbConfig` (env config, mirrors `QaConfig`)

```java
public record DbConfig(String host, int port, String database, String user, String password) {
    public static DbConfig fromEnv() { return fromEnv(System::getenv); }
    public static DbConfig fromEnv(Function<String, String> lookup) { ... }
    String jdbcUrl() { return "jdbc:postgresql://%s:%d/%s".formatted(host(), port(), database()); }
}
```

Env vars `QA_DB_HOST` (default `localhost`), `QA_DB_PORT` (default `5432`),
`QA_DB_NAME` (default `notificationdb`), `QA_DB_USER` (default `postgres`),
`QA_DB_PASSWORD` (default `postgres`) — defaults match the target service's
own `.env.example`, exactly like `QaConfig.baseUrl()`'s default matches its
default `8080`. Logged at INFO on construction (password redacted), same
convention as `QaConfig.fromEnv()`. Immutable record → thread-safe.

### 3. `db` — `PostgresDatabase` (the oracle primitive)

Generic, table-agnostic — no "notification" anywhere in this module (library
code, per the architecture skill's "no project-specific names" rule):

```java
public final class PostgresDatabase {
    public PostgresDatabase(DbConfig config) { ... }

    public <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection c = DriverManager.getConnection(config.jdbcUrl(), config.user(), config.password());
             PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapper.map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseOracleException("Query failed: " + sql, e);   // fail loud, never swallowed
        }
    }
}

@FunctionalInterface
public interface RowMapper<T> { T map(ResultSet rs) throws SQLException; }
```

`DatabaseOracleException extends RuntimeException` — every `SQLException`
(bad SQL, connection refused, type mismatch) surfaces as a hard test failure,
never a caught-logged-and-continued warning. One connection per call
(try-with-resources, opened and closed within the method) — no pooled/shared
connection, no singleton, matching "Builder-configured per instance...no
singleton connection." A `queryList` overload (same shape, returns `List<T>`)
covers the rare list case, but the reference is explicit that this is
"read-mostly" — no update/insert helpers beyond what's needed here.

Javadoc states thread-safety: safe to share across threads (no mutable
state — `DbConfig` is immutable, no cached `Connection`), but each call opens
its own connection so callers still get isolation between concurrent test
threads.

### 4. `db` — Testcontainers self-tests

`db/src/test/java/dev/qacommons/db/PostgresDatabaseTestcontainersTest.java`:
a real `@Testcontainers` Postgres 16 container (matching the target service's
own image), a minimal test table created via a plain `Statement` in
`@BeforeAll`, then `PostgresDatabase` exercised against it — row found, row
absent (`Optional.empty()`), `PreparedStatement` param binding (a value
containing a quote, proving no string concatenation), and a deliberately bad
query proving `DatabaseOracleException` — never a swallowed/logged failure.

Guarded the same way `PlaywrightExtension` guards a missing Chromium install:
`Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), ...)`
in `@BeforeAll`, so a machine without Docker gets a graceful **skip**, not a
failure — `mvn clean verify` stays green with neither the target service nor
Docker running. These are **not** `@Tag("live")` (no external service
involved, self-contained container) — they run by default, same tier as
`ui`'s local Playwright tests.

### 5. `template` — the domain-specific oracle + proof test

`template` owns the notification-specific knowledge (matches how
`NotificationsEndpoint` wraps `Endpoint` — `db` is the generic primitive,
`template` is the living example that names real columns):

```java
public record NotificationRow(
    String id, String recipient, String channel,
    String lockedBy, LocalDateTime lockedAt, int attemptsCount) {}

public final class NotificationsOracle {
    public NotificationsOracle(DbConfig config) { this.db = new PostgresDatabase(config); }

    public Optional<NotificationRow> findById(String id) {
        return db.queryOne(
            "SELECT id, recipient, channel, locked_by, locked_at, attempts_count "
                + "FROM notifications WHERE id = ?",
            rs -> new NotificationRow(rs.getString("id"), rs.getString("recipient"),
                    rs.getString("channel"), rs.getString("locked_by"),
                    rs.getObject("locked_at", LocalDateTime.class), rs.getInt("attempts_count")),
            id);
    }
}
```

Only the columns the proof test actually asserts — `status`/`payload`/
`created_at`/etc. are left out deliberately (extend when a real scenario
needs them, not speculatively).

`template/pom.xml` gains `qa-commons-db` (new internal dependencyManagement
entry in the root pom, alongside `core`/`api`/`ui`/`template`).

Proof test, `template/src/test/java/dev/qacommons/template/tests/NotificationOracleTest.java`,
`@Tag("live")`:
```java
NotificationsEndpoint endpoint = new NotificationsEndpoint(config);
NotificationResponse sent = endpoint.send(request).expectSuccess();

Optional<NotificationRow> row = oracle.findById(sent.notificationId());

assertThat(row).isPresent();
assertThat(row.get().recipient()).isEqualTo(request.recipient());
assertThat(row.get().channel()).isEqualTo(request.channel().name());
assertThat(row.get().attemptsCount()).isGreaterThanOrEqualTo(0);   // column is readable, not a specific claim state
```
Deliberately **not** asserted: `status`, `lockedBy`/`lockedAt` *values* — the
poller can claim the row at any point after intake, so only "the claim
columns exist and are queryable" is asserted, never "and hold value X" (the
Q1 lesson, restated in the reference as "nothing timing-dependent").

**Open question to confirm during implementation** (flagging now rather than
guessing, same discipline as `qa-commons/plan.md`'s T9 live-probing note):
whether the row is committed synchronously before the `202` response returns,
or async shortly after. The `V1.0.1` migration's framing ("the notifications
table becomes the queue itself") implies synchronous intake-write (an outbox-
style design), so the expectation is **no retry needed** — this gets
confirmed against the real running service before the test is considered
done, and the observed behavior gets recorded either way. **If** a retry
turns out to be necessary, it's Awaitility (test scope), per the
test-architecture skill's "Thread.sleep is BANNED" rule — never a hand-rolled
sleep loop — and still only polls for row *existence*, never for a specific
`status`/claim value.

### 6. `core` — the `Reporter` abstraction

```java
// dev.qacommons.core.report
public interface Reporter {
    void parameter(String name, String value);
    void attachment(String name, String mimeType, Path file);

    static Reporter fromClasspath() { ... }   // Slf4jReporter, or the Allure bridge if Allure is present
}
```

- `Slf4jReporter` — the zero-dependency default (`core` already depends on
  `slf4j-api`, nothing new). `parameter` logs at INFO; `attachment` logs the
  file path at INFO — no-ops functionally, matching what `ui` already does
  today for its screenshots (`LOGGER.error("...saved to {}", path)`), so
  behavior is unchanged for any consumer that never adds Allure.
- `internal.AllureReporterBridge` — the **only** place in `core` that ever
  names Allure, and it never imports it: `Class.forName("io.qameta.allure.Allure")`
  guards a small reflective adapter (`Allure.parameter(String,String)` and
  `Allure.addAttachment(String,String,InputStream,String)` — two stable,
  long-unchanged static methods). `core`'s own `pom.xml` gains **zero**
  Allure dependency, not even `optional`/`provided` — satisfying "no
  compile-time Allure dependency ever enters `core`" structurally, the same
  way the `db`-module decision does for JDBC.
- `Reporter.fromClasspath()` decides once per call (cheap: a `Class.forName`
  guarded by a cached boolean) whether Allure classes are visible on the
  *caller's* runtime classpath — which depends entirely on what that module
  chose to depend on, never on `core`.
- Failure handling is **not** the oracle's fail-loud rule: if Allure is
  detected but a reflective call still fails (unexpected version skew), that
  logs a WARN and the test continues — a broken *report attachment* must
  never fail an otherwise-passing test. This is a deliberate, documented
  divergence from the DB oracle's "throw, never swallow" rule: the oracle
  *is* the assertion; the reporter is a diagnostic side-channel.
- Tested for real: `core/pom.xml` adds `allure-java-commons` at **test scope
  only**, purely so `AllureReporterBridge`'s reflective path has something
  real to call and assert against in `core`'s own tests. Test-scope
  dependencies never propagate to consumers, so this doesn't reopen the
  "zero Allure dependency" guarantee for anyone depending on `qa-commons-core`.

`ReportContextExtension implements BeforeEachCallback` (also in
`core.report`): builds `QaConfig.fromEnv()` and calls
`Reporter.fromClasspath().parameter("datafakerSeed", ...)` /
`.parameter("baseUrl", ...)`. Added via `@ExtendWith` to `template`'s and
`ui`'s live test classes (task 7's "attached to every test") — explicit
per-class opt-in, matching this repo's existing style (no auto-registration
magic anywhere else in the codebase).

### 7. `ui` — screenshots/traces through `Reporter`

`ui/pom.xml` gains `allure-junit5` at **test scope only** (mirrors `db`'s
Testcontainers scoping reasoning) — this repo's own `ui` test runs get real
Allure output; nobody who depends on `qa-commons-ui` as a library inherits
Allure, since test-scope dependencies never propagate downstream. `ui`
remains the Allure-optional module the reference asks for; `template`'s
already-mandatory Allure dependency is untouched.

`PlaywrightExtension.afterEach`'s failure branch and `UiSoftAssertions`'s
failure callback keep their existing disk-save + SLF4J-log behavior
unchanged, and additionally call
`Reporter.fromClasspath().attachment("screenshot", "image/png", screenshotPath)`
/ `.attachment("trace", "application/zip", tracePath)` right after the file
is written. Purely additive — no existing behavior removed, matching the
mission's "existing module surfaces unchanged except additive" constraint.

### 8. Report generation — `allure-maven`

Verified against Allure's own current docs (`allurereport.org/docs/`), not
assumed:
- Plugin: `io.qameta.allure:allure-maven` (Maven Central, current release
  **3.0.2**). It manages its own report-generation engine — no separately
  installed Allure commandline needed.
- It defaults to the Allure 3 (Node.js-managed) report engine, but supports
  `<reportVersion>` to pin the engine to the Allure 2 line instead.
  **Tried, then reverted**: pinning `<reportVersion>${allure-bom.version}</reportVersion>`
  (2.29.1) fails outright — that path resolves `allure-commandline` as a
  plain Maven artifact (`io.qameta.allure:allure-commandline:zip:2.29.1`),
  which isn't published to Maven Central under that coordinate/packaging, so
  the goal fails before it ever reads a result file. The **default**
  (unpinned) engine, which downloads Allure 3 (observed: 3.4.1) via its own
  managed Node.js runtime rather than Maven artifact resolution, works and
  correctly reads the Allure 2-format `allure-results` `api`/`template`/`ui`
  already write — Allure 3's report generator is designed to be backward-
  compatible with the Allure 2 result-file format, and that held up in
  practice. No `<reportVersion>` configured at all.
- Goal: `mvn allure:report` → static HTML under
  `target/site/allure-maven-plugin/index.html` by default (path itself
  reconfigurable via `reportDirectory`, but the default is fine here).

**One combined report, both `ui` screenshots and `template`/`api` request
pairs**: today each module's Allure listener writes to its own
`<module>/allure-results/` (verified empirically — `api`, `template`, `perf`
already do this; there's no `allure.properties` anywhere, so this is the
library's own built-in relative-path default). To get one report instead of
per-module ones:
- `ui` and `template` both get `allure.results.directory` set to the **same**
  shared path via Surefire `<systemPropertyVariables>`:
  `${maven.multiModuleProjectDirectory}/allure-results` (a real, stable
  Maven-3.3.1+ built-in property pointing at the reactor root regardless of
  which module's Surefire run sets it — to be double-checked empirically
  during implementation since this is the one piece of new-to-this-repo
  Maven plumbing in this plan).
- The root `pom.xml` (packaging `pom`) declares `allure-maven` directly in
  its own `<build><plugins>` (not `<pluginManagement>`, since it must
  actually run) with `<inherited>false</inherited>` — this stops the
  *configuration* (`resultsDirectory` etc.) from being inherited by child
  POMs, so a child invoking the goal falls back to the plugin's own
  (module-relative, empty-for-us) defaults instead of silently duplicating
  the shared report. **It does not, by itself, scope which reactor modules a
  directly-invoked goal runs against** — the same gotcha the `perf` mission
  hit with `gatling:test`: a bare `mvn allure:report` from repo root, with no
  `-N`, still walks the *entire* reactor and attempts the goal in every
  module, not just root. Actual scoping is `-N` (`--non-recursive`) on the
  command line, which restricts the goal to the root project only — that's
  the real mechanism, `<inherited>false</inherited>` is a secondary guard
  against child modules producing a misleading duplicate/empty report if
  someone runs the bare (undocumented) form anyway. `resultsDirectory` points
  at `${project.basedir}/allure-results` (root's own basedir *is* the
  reactor root, so no property trick needed on this side).
- Version pinned once in root `pluginManagement`, matching the existing
  `gatling-maven-plugin`/`exec-maven-plugin` convention of pinning version
  centrally and configuring actual execution per-module (or, here, at root
  only).

Documented flow (root `README.md`):
```
mvn -pl ui,template -am test -DrunLive=true   # existing command, now also
                                               # populates the shared allure-results
mvn -N allure:report                          # new — non-recursive: root only,
                                               # generates the combined report
```
then open `target/site/allure-maven-plugin/index.html`. The bare
`mvn allure:report` (no `-N`) is explicitly **not** the documented command —
verified empirically (T11) to walk the whole reactor instead of just root,
per the `<inherited>false</inherited>` note above. `mvn clean verify`
never runs `allure:report` (no execution bound to the default lifecycle,
same "declared, not bound" idiom `perf`'s Gatling plugin and `ui`'s
browser-install already use) — report generation is always an explicit,
separate step.

### Failure modes

- Target service down → DB oracle test tagged `@Tag("live")`, excluded by
  default, same gating as every other live test; the Testcontainers self-test
  needs no target service at all.
- Docker not installed/running → Testcontainers self-test skips gracefully
  (`Assumptions`), doesn't fail the build.
- Allure absent from a module's classpath → `Reporter.fromClasspath()`
  returns `Slf4jReporter`, everything still runs, just without report
  attachments — never a hard failure.
- Bad SQL / DB unreachable in the oracle → `DatabaseOracleException`, loud,
  uncaught — fails the test, which is the correct outcome (unlike Allure
  attachment failures).

## Risks & open questions

- **Row-write timing** (sync-before-202 vs. async) — flagged above, resolved
  by direct observation against the live service during implementation
  rather than assumed; plan.md gets amended here if the assumption is wrong,
  same discipline as `qa-commons/plan.md`'s T9.
- **`mvn -N allure:report` scoping** — confirmed by direct precedent
  (`gatling:test` taught the same lesson in the `perf` mission): a bare goal
  invocation walks the whole reactor regardless of plugin `<inherited>`
  settings; only `-N` actually restricts it to the root project. T11 proves
  this empirically both ways (documented `-N` form produces exactly one
  report; bare form does not) rather than asserting it from memory.
- **`${maven.multiModuleProjectDirectory}` in Surefire's
  `systemPropertyVariables`** — standard and stable, but this specific
  combination (built-in reactor property → Surefire fork JVM system
  property → picked up by `allure-java-commons`' own results-directory
  resolution) is new plumbing for this repo and gets a real empirical check
  (write from both `ui` and `template`, confirm both land in one directory)
  before being called done.
- **`allure-maven` 3.0.2 report engine choice** — `reportVersion=2.29.1` was
  tried and failed for real (`allure-commandline:zip:2.29.1` unresolvable
  from Central), so the plan reverted to the unpinned default (Allure 3,
  3.4.1 observed) — confirmed in T11 to correctly read the existing Allure
  2-format results. The actual rendered report (real `ui` screenshot and
  `api` request/response attachments) still gets opened and eyeballed per
  the mission's "verified by opening it, not by the plugin running"
  instruction (T12), not just trusted from a successful `BUILD SUCCESS`.
- **Reflective `AllureReporterBridge`** — the one piece of reflection
  anywhere in this codebase (everything else is fully typed). Justified by
  the "zero Allure dependency in `core`, ever" constraint; kept intentionally
  tiny (two static-method calls) and covered by `core`'s own test-scope-only
  Allure dependency so the reflective path is actually exercised, not just
  hoped to work.
