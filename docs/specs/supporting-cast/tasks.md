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

- [x] T11: `allure-maven` wiring for combined report generation — files: root
  `pom.xml` (+`allure-maven-plugin.version` property, `pluginManagement` entry,
  root-only `<build><plugins>` entry with `<inherited>false</inherited>` and
  `resultsDirectory` pointed at `${project.basedir}/allure-results`),
  `ui/pom.xml` + `template/pom.xml` (Surefire `systemPropertyVariables` →
  `allure.results.directory` = `${maven.multiModuleProjectDirectory}/allure-results`)
  — done: verified live, all of it.

  `${maven.multiModuleProjectDirectory}` worked exactly as expected: after
  `mvn -pl ui,template -am test -DrunLive=true`, both modules' results landed
  in the one shared root `allure-results/` (31 entries, both `SwaggerUiTest`
  and `NotificationOracleTest` results present; no per-module `allure-results`
  dir left behind). `mvn clean verify` never triggers `allure:report` (`site`
  is a separate Maven lifecycle the plugin binds to on its own, confirmed via
  `maven-help-plugin:describe`).

  Scoping confirmed empirically both ways per the amendment: `mvn -N
  allure:report` generated exactly one report
  (`target/site/allure-maven-plugin/index.html`, 29 tests in `summary.json`);
  a bare `mvn allure:report` (no `-N`), run once on purpose, executed the
  goal in **all 7** reactor projects (root + every child), producing 7
  separate report directories - confirmed the exact `gatling:test`-style
  gotcha and that `-N` (not `<inherited>false</inherited>`) is the real
  scoping mechanism. Cleaned up the 6 stray per-module report dirs afterward.

  One real deviation from plan.md: pinning `<reportVersion>${allure-bom.version}</reportVersion>`
  (2.29.1) **failed** - `io.qameta.allure:allure-commandline:zip:2.29.1` isn't
  resolvable from Maven Central under that coordinate. Reverted to the
  unpinned default engine (Allure 3, resolved 3.4.1 via its own managed
  Node.js download, not Maven artifact resolution) - confirmed it correctly
  reads the existing Allure 2-format `allure-results`. plan.md's Design
  section updated to match what actually works, not what was originally
  proposed.

- [x] T12: open and verify the report, then README — files: root `README.md`
  (new "Reporting" section + `db` module entry), `db/README.md` (new: setup,
  Testcontainers/Docker prerequisite, `QA_DB_*` env vars, self-tests, live
  oracle proof, the port-5432 collision writeup), `template/README.md` (DB
  oracle proof section) — done: the report was actually opened, not just
  generated.

  Served `target/site/allure-maven-plugin` locally and drove it with a real
  headless Chromium via Playwright (already a repo dependency) to take
  genuine screenshots of the rendered app - not just inspecting the build
  log. Confirmed all three required elements by looking at the screenshots:
  (a) `PlaywrightExtensionFailureDiagnosticsTest.FailingCase.hardFailure`
  shows "Attachments 2": `trace` (application/zip, 1.9 KiB) and `screenshot`
  (image/png, 4.2 KiB); (b) `NotificationsTest.send_returns202Queued` shows a
  `Request` attachment with real content (`POST to
  http://localhost:8080/api/v1/notifications/send`); (c) the same test's
  "Parameters" section lists `datafakerSeed` and `baseUrl` with real values.
  29 total tests in the combined report, 1 broken (the intentional
  `hardFailure` case), matching the live run.

  Re-verified `mvn clean verify` from a clean state (removed
  `allure-results/`/`target/site`/`.allure`) — BUILD SUCCESS, 65 tests across
  all 7 projects, `qa-commons-template` shows 0 tests (live suite excluded by
  default).

  **Partial gap, disclosed rather than hidden**: did not verify the
  "Docker stopped" branch of `db`'s self-tests by actually stopping Docker -
  that would take down every other container on this machine (the
  notification service, redis, grafana, ngrok, ...), a disruptive action out
  of proportion to this one check. Tried two non-disruptive simulations
  first - a bogus `DOCKER_HOST` and `TESTCONTAINERS_HOST_OVERRIDE` - neither
  overrode the cached `~/.testcontainers.properties` `tc.host` entry, so
  Testcontainers kept finding the real daemon either way. Resting on
  structural proof instead: the guard
  (`Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable())`)
  is the identical pattern already empirically proven for `ui`'s
  Chromium-missing skip, and `isDockerAvailable()` is Testcontainers' own
  documented API for exactly this check - not independently re-verified live
  in this session.

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
