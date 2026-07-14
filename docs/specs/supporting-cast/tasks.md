# Tasks: supporting-cast

Branch `feature/supporting-cast`, cut from `main` before T1. One task, one
commit, in order.

- [x] T1: `db` module scaffold — files: root `pom.xml` (+`<module>db</module>`,
  `postgresql`/`testcontainers-bom` version properties + `dependencyManagement`
  entries, `qa-commons-db` internal coordinate), `db/pom.xml` (postgresql main
  scope, slf4j-api main scope, testcontainers-bom import + testcontainers-postgresql
  test scope, junit-jupiter + assertj-core test scope) — done when: `mvn clean
  verify` passes with the new empty module resolving in the reactor.

- [x] T2: `db` — `DbConfig` env-based record — files:
  `db/src/main/java/dev/qacommons/db/config/DbConfig.java`, test — done when:
  `mvn -pl db test` passes; covers defaults (`localhost`/`5432`/`notificationdb`/
  `postgres`/`postgres`), override via the `fromEnv(Function<String,String>)`
  seam, and `jdbcUrl()` formatting.

- [x] T3: `db` — `PostgresDatabase` oracle primitive + `DatabaseOracleException` +
  `RowMapper` — files: `db/src/main/java/dev/qacommons/db/PostgresDatabase.java`,
  `db/src/main/java/dev/qacommons/db/RowMapper.java`,
  `db/src/main/java/dev/qacommons/db/DatabaseOracleException.java` — done when:
  compiles; `PreparedStatement`-only (grep confirms no string-concatenated SQL),
  every method try-with-resources on its `Connection`, no caught-and-swallowed
  `SQLException` anywhere (grep confirms no bare `catch (SQLException` without
  a rethrow).

- [x] T4: `db` — Testcontainers self-tests — files:
  `db/src/test/java/dev/qacommons/db/PostgresDatabaseTestcontainersTest.java`
  (+ testcontainers-junit-jupiter dependency if needed beyond the bare
  `testcontainers-postgresql` module) — done when: with Docker running,
  `mvn -pl db test` passes covering row-found, row-absent (`Optional.empty()`),
  a quote-containing param value (proves parameterization), and a deliberately
  bad query throwing `DatabaseOracleException`; with Docker stopped, the same
  command reports these tests **skipped**, not failed, and `mvn clean verify`
  from repo root stays green either way.

- [x] T5: `template` — `NotificationsOracle` + `NotificationRow` — files:
  `template/pom.xml` (+qa-commons-db), `template/src/main/java/dev/qacommons/template/db/NotificationsOracle.java`,
  `template/src/main/java/dev/qacommons/template/db/NotificationRow.java` — done
  when: `mvn -pl template -am compile` succeeds; `findById` queries exactly the
  real `notifications` table columns confirmed against the live service's
  migrations (`id`, `recipient`, `channel`, `locked_by`, `locked_at`,
  `attempts_count`).

- [x] T6: `template` — live DB oracle proof test — files:
  `template/src/test/java/dev/qacommons/template/tests/NotificationOracleTest.java`
  — done: real send → oracle lookup → identity + claim-column assertions,
  **no retry/Awaitility needed** — the row was found on the very first
  lookup on both runs, confirming the row-write is synchronous with the
  `202` response (the outbox-design expectation from plan.md was correct).
  Two consecutive `mvn -pl template test -DrunLive=true -Dtest=NotificationOracleTest`
  runs passed clean; `mvn -pl template test` (no flag) passed with zero
  tests executed. Hit a real environment issue en route - see "Deviations /
  decisions" below (port 5432 collision) - resolved without touching the
  oracle code itself.

- [ ] T7: `core` — `Reporter` interface + `Slf4jReporter` + reflective
  `AllureReporterBridge` — files: `core/pom.xml` (+allure-java-commons, test
  scope only), `core/src/main/java/dev/qacommons/core/report/Reporter.java`,
  `core/src/main/java/dev/qacommons/core/report/Slf4jReporter.java`,
  `core/src/main/java/dev/qacommons/core/report/internal/AllureReporterBridge.java`,
  tests — done when: `mvn -pl core test` passes; a test with Allure on the test
  classpath asserts `Reporter.fromClasspath()` returns the Allure-backed
  reporter and that `parameter`/`attachment` calls actually reach
  `io.qameta.allure.Allure`'s real API (e.g. via `Allure.getLifecycle()`
  state or a captured `AllureResultsWriter`); a scan of `core/src/main/java`
  confirms zero non-test-scope Allure dependency in `core/pom.xml`.

- [ ] T8: `core` — `ReportContextExtension` — files:
  `core/src/main/java/dev/qacommons/core/report/ReportContextExtension.java`,
  test — done when: `mvn -pl core test` passes; test asserts `beforeEach`
  calls `Reporter.parameter` with the seed and base URL from `QaConfig.fromEnv()`
  (verified via a test-double `Reporter` injected through a package-visible
  seam, not real Allure).

- [ ] T9: wire `ReportContextExtension` into the live test classes — files:
  `template/src/test/java/dev/qacommons/template/tests/NotificationsTest.java`,
  `template/src/test/java/dev/qacommons/template/tests/NotificationOracleTest.java`,
  `ui/src/test/java/dev/qacommons/ui/tests/SwaggerUiTest.java` — done when: each
  gains `@ExtendWith(ReportContextExtension.class)`; a live run
  (`-DrunLive=true`) followed by inspecting the raw `allure-results` JSON for
  one of these tests shows `datafakerSeed`/`baseUrl` parameters attached.

- [ ] T10: `ui` — screenshots/traces through `Reporter` — files: `ui/pom.xml`
  (+allure-junit5, test scope only), `ui/src/main/java/dev/qacommons/ui/PlaywrightExtension.java`,
  `ui/src/main/java/dev/qacommons/ui/UiSoftAssertions.java` — done when: `mvn -pl
  ui test` still passes (existing disk-save + log behavior unchanged); a
  deliberately-failing test (reusing `PlaywrightExtensionFailureDiagnosticsTest`'s
  approach) additionally shows a screenshot/trace attachment in that test's raw
  `allure-results` entry, not just a file on disk.

- [ ] T11: `allure-maven` wiring for combined report generation — files: root
  `pom.xml` (+`allure-maven-plugin.version` property, `pluginManagement` entry
  with `reportVersion` pinned to `allure-bom.version`, root-only `<build><plugins>`
  entry with `<inherited>false</inherited>` and `resultsDirectory` pointed at
  `${project.basedir}/allure-results`), `ui/pom.xml` + `template/pom.xml`
  (Surefire `systemPropertyVariables` → `allure.results.directory` =
  `${maven.multiModuleProjectDirectory}/allure-results`) — done when: after
  `mvn -pl ui,template -am test -DrunLive=true`, both modules' results land in
  the one shared directory (confirmed by listing it); `mvn clean verify` never
  triggers `allure:report` (no default-lifecycle binding); `mvn -N allure:report`
  run once at repo root generates exactly one
  `target/site/allure-maven-plugin/index.html` and no per-module report
  anywhere else in the tree; and, separately, a bare `mvn allure:report` (no
  `-N`) is run once to empirically **prove it is the wrong command** — same
  gotcha `gatling:test` taught in the `perf` mission — recording here what it
  actually does (walks every reactor module) so the root README's documented
  command is the verified `-N` form, not an assumption.

- [ ] T12: open and verify the report, then README — files: root `README.md`
  (new "Reporting" section: the two-command flow, what should be visible),
  `db/README.md` (new: setup, Testcontainers/Docker prerequisite, `QA_DB_*`
  env vars, running the self-tests and the live oracle proof), `template/README.md`
  (DB env vars + the oracle proof test) — done when: the generated report is
  actually opened in a browser and visually confirmed to contain (a) at least
  one `ui` test with a screenshot or trace attachment, (b) at least one
  `template`/`api` test with a request/response attachment, (c) a test showing
  `datafakerSeed`/`baseUrl` as parameters — per the mission's "verified by
  opening it, not by the plugin running." Also re-verify `mvn clean verify`
  from a clean state with both the target service AND Docker stopped —
  BUILD SUCCESS, zero live tests, zero DB-self-tests failed (skipped instead).

## Deviations / decisions

- **T6**: the local dev machine has a genuine port collision on 5432 - a
  native, non-Docker `postgres.exe` (unrelated to this mission) also listens
  on `0.0.0.0:5432`, and Git Bash/native-Windows traffic to `localhost:5432`
  was landing on that native process instead of the notification service's
  Docker-forwarded container (confirmed by the container's own logs showing
  zero connection attempts during the failed runs, and by a standalone JDBC
  probe reproducing "password authentication failed" against the wrong
  server). Resolved per the user's direction: remapped the *published* port
  only, `DB_PORT=15432` in the service's own `.env` + `docker-compose up -d
  postgres` to recreate just that container - the app is unaffected, since
  it reaches Postgres over the internal Docker network by service name, not
  the published port (confirmed: `/q/health/ready` stayed UP throughout).
  `QA_DB_PORT=15432` on the test run. Explicitly did **not** stop the native
  install - never touch a system service the user didn't ask to have
  stopped. Verified we were actually hitting the right database (not just a
  database) by querying `flyway_schema_history` before trusting any test
  result - all 4 known migrations (`1.0.0`-`1.0.3`) present. This is a
  documented known-collision case in `db/README.md` (T12), not a framework
  bug - `qa-commons-db`'s own Testcontainers self-tests (T4) were unaffected
  throughout since Testcontainers uses a dynamically-assigned port.
