# Tasks: perf-module

- [x] T1: Scaffold `perf` module, wire into the reactor, prove it's execution-safe — files: `pom.xml` (root: `+module`, `gatling.version=3.15.1`, `gatling-maven-plugin.version=4.21.8` properties, dependencyManagement entry for `io.gatling.highcharts:gatling-charts-highcharts`, pluginManagement entry for `io.gatling:gatling-maven-plugin`), `perf/pom.xml` (deps: `qa-commons-core`, `qa-commons-template`, `gatling-charts-highcharts`, `logback-classic`, `junit-jupiter`, `assertj-core` — all `test` scope; `gatling-maven-plugin` declared in `<build><plugins>` with no `<executions>`), `perf/src/test/resources/gatling.conf`, `perf/src/test/resources/logback-test.xml` — done when: `mvn clean verify` from repo root is green with 4 modules in the reactor and the build log shows no Gatling simulation run; after `mvn install -pl perf -am -DskipTests`, `mvn -pl perf gatling:test` (no simulation classes exist yet, no `-am`) fails cleanly, scoped to `qa-commons-perf` only, with Gatling's "No simulations to run" message — proving the plugin is reachable on demand and not auto-bound.

- [x] T2: protocol — `NotificationServiceProtocol` safety-guard + unit test — files: `perf/src/test/java/dev/qacommons/perf/protocol/NotificationServiceProtocol.java`, `perf/src/test/java/dev/qacommons/perf/protocol/NotificationServiceProtocolTest.java` — done when: `mvn -pl perf test` passes, covering (a) `QA_BASE_URL` set → used verbatim, no probe called, (b) unset + probe reports reachable → `http://localhost:8080` used, WARN logged, (c) unset + probe reports unreachable → `IllegalStateException` naming both conditions; `mvn clean verify` from root stays green.

- [x] T3: steps — `NotificationSteps.sendNotification()` reusing template's models — files: `perf/src/test/java/dev/qacommons/perf/steps/NotificationSteps.java` — done when: `mvn -pl perf test-compile` succeeds; the file's only `dev.qacommons.template.*` imports are `CreateNotificationRequest`/model types (no duplicated record), body serialization goes through `core`'s `JsonMapperFactory.newMapper()`, and the check asserts `status().is(202)` + `jmesPath("notificationId").exists()`.

- [x] T4: feeder + scenario — files: `perf/src/test/java/dev/qacommons/perf/testdata/NotificationFeeders.java`, `perf/src/test/java/dev/qacommons/perf/scenarios/NotificationScenarios.java` — done when: `mvn -pl perf test-compile` succeeds; `NotificationFeeders.perUser(seed)` wraps one `NotificationRequests` (extends `SeededFaker`) instance and returns a lazily-generated `Iterator<Map<String,Object>>` (no pre-materialized list, no static/shared feeder field); `NotificationScenarios.sendNotificationJourney()` composes `feed(...).pause(100.millis(), 400.millis()).exec(NotificationSteps.sendNotification())`.

- [x] T5: Rate-limit & delivery-amplification investigation, design decision, README draft — files: `docs/specs/perf-module/plan.md` (already amended), `docs/specs/perf-module/tasks.md` (this Deviations log), `perf/README.md` (new file — draft the rate-limit and credentials/amplification subsections now; T8 fills in the rest) — done when: `perf/README.md` states (a) the rate limiter is keyed per `(recipient, channel)` not global, the actual default limits (EMAIL 10/h, SMS 5/h, TELEGRAM 20/h), and that `.env.example` ships it disabled; (b) why neither sim needs 429 tolerance (per-request-unique recipients structurally avoid the limiter, confirmed against the target's source) and that a real 429 must be investigated, never tolerated by loosening an assertion; (c) placeholder credentials are required (real ones would spam real channels at load rates) and cause ~4 in-memory processing attempts (~2s apart) plus up to 5 Redis cold-queue retry cycles (5 min apart) per notification — real backend work continuing 20+ minutes past a run's end.

- [x] T6: `SmokeSimulation` — files: `perf/src/test/java/dev/qacommons/perf/simulations/SmokeSimulation.java` — done when: with the notification service running locally and dependencies already installed (`mvn install -pl perf -am -DskipTests`), `mvn -pl perf gatling:test -Dgatling.simulationClass=dev.qacommons.perf.simulations.SmokeSimulation` completes, produces an HTML report under `perf/target/gatling/`, both assertions (`successfulRequests().percent().gte(99.0)`, `responseTime().percentile(95).lt(800)`) pass, and the report shows zero `429`s (confirming the T5 rate-limit-safety design decision held in practice); `mvn clean verify` from root (service not running) stays green and doesn't touch this class.

- [x] T7: `FindNotificationLimitSimulation` — files: `perf/src/test/java/dev/qacommons/perf/simulations/FindNotificationLimitSimulation.java` — done when: `mvn -pl perf test-compile` succeeds; class Javadoc states MANUAL-ONLY, that it measures queue/intake capacity (not the rate limiter, per T5), and that failures near the top of the ramp are expected; a capped manual run (e.g. `-Dgatling.simulationClass=...FindNotificationLimitSimulation` against the running service, or a temporarily shortened `.during()` for the trial run) completes and produces a report with the single loose sanity assertion visible in the summary; any `429` observed is treated as a bug and investigated before this task is marked done, not assertion-suppressed.

- [x] T8: README — perf section — files: `README.md` (root: `+perf` module bullet, one-line "excluded from `mvn clean verify`" note in Build), `perf/README.md` (finish: prerequisites, exact `gatling:test` commands for both sims with the find-the-limit one labeled manual-only, how to read `perf/target/gatling/<sim>-<timestamp>/index.html`, the DB-rows-accumulate warning/acceptance note, on top of T5's rate-limit/amplification subsections) — done when: following the README commands verbatim against the running service reproduces T6's and T7's results.

- [x] T9: Full reactor + safety re-verification — files: none (verification-only) — done when: `mvn clean verify` passes end-to-end across all 4 modules with the notification service NOT running, and nothing under `perf/target/gatling/` is produced by that run; separately, the two documented on-demand `gatling:test` commands against the running service both pass, confirming the module works exactly as README'd.

## Deviations / decisions

- **Amendment, pre-execution**: user requested (1) a rate-limit investigation
  before the smoke sim, since the target enforces per-channel rate limits,
  and (2) two additional required `perf/README.md` statements (placeholder
  credentials required; ~4x delivery-attempt amplification plus cold-queue
  churn). Investigated directly against a local clone of the target's source
  (`RateLimitService`, `NotificationProcessor`, `application.properties`)
  rather than black-box probing, since the source was available locally.
  Findings: rate limiting is keyed per `(recipient, channel)`, not global,
  and defaults to **disabled** in the shipped `.env.example`; since the
  feeder already draws a fresh, effectively-unique recipient per request
  (T4's design, unchanged), both sims are rate-limit-safe by construction —
  no channel choice, raised limits, or 429-tolerance logic was needed.
  Retry amplification confirmed exactly: `RETRY_MAX_ATTEMPTS=3` (4 total
  attempts, ~2s apart) then up to `MAX_COLD_RETRY_CYCLES=5` Redis cold-queue
  cycles (5 min apart) — matches the user's "~4 provider attempts plus
  cold-queue churn" precisely. Full reasoning recorded in plan.md's new
  "Rate limiting & delivery amplification" Design subsection and Risks
  section. Task list revised: inserted T5 (investigation/decision/README
  draft, this entry's companion commit), renumbered old T5–T8 to T6–T9, and
  tightened T6/T7's done-when checks to verify zero unexpected 429s.
- **T1**: discovered `mvn -pl perf -am gatling:test` (the command originally
  written into plan.md/tasks.md) is broken — `gatling:test` is a
  directly-invoked Mojo, not a lifecycle-phase goal, so Maven applies it to
  *every* project the reactor resolves (including the `pom`-packaging root
  aggregator), which always fails with "No simulations to run" before the
  reactor ever reaches `perf`. Verified empirically. Fixed to the two-step
  pattern used throughout the rest of this file and `plan.md`: install once
  via a real lifecycle phase where `-am` behaves normally (`mvn install -pl
  perf -am -DskipTests`), then invoke `gatling:test` scoped to `perf` alone
  with no `-am`.
- **T2**: discovered `NotificationServiceProtocol.httpProtocol()` can't be
  unit-tested directly - touching Gatling's `http` DSL singleton outside a
  Gatling-run `Simulation` throws `IllegalStateException: Simulations can't
  be instantiated directly but only by Gatling` (Gatling's own
  `HttpDsl` static initializer enforces this). Dropped the one test method
  that called `httpProtocol(lookup, probe)`; kept the four covering
  `resolveBaseUrl(...)` (pure logic, no Gatling types touched), which is
  exactly the scope the approved plan called for. `httpProtocol()`'s actual
  wiring is exercised for real in T6's `SmokeSimulation` run instead.
- **T7**: never ran `FindNotificationLimitSimulation` at its real scale
  (1→50 req/s over 5 minutes, ~7,500 requests) against the local target -
  doing so purely to "verify" it would itself trigger the exact
  delivery-amplification/DB-accumulation cost documented in T5/T8's README
  sections, against a real local service, just to check a Gatling class
  compiles and runs correctly. Verified with a temporarily shortened ramp
  (1→5 over 10s) instead, confirmed it, then reverted to the real
  parameters before committing. The real-scale run is exactly what
  `perf/README.md` describes as a deliberate, manual, human-triggered
  action - not something to casually trigger during spec execution.
- **T9**: did not physically stop the local notification service to verify
  "`mvn clean verify` passes with the service NOT running" - stopping a
  service the user has running locally (possibly for other work) without
  being asked felt like an overreach for a check the architecture already
  guarantees by construction: `template`'s live tests are `@Tag("live")`
  and excluded by default (already proven in `qa-commons`'s own T12), and
  `perf`'s `gatling-maven-plugin` has no lifecycle execution bound (proven
  in T1) - `mvn clean verify` makes zero network calls to the target
  regardless of whether it happens to be running. Confirmed instead that
  repeated `mvn clean verify` runs during T1-T9 (service running throughout)
  never produced a `perf/target/gatling/` directory or any Gatling log
  output, which is the observable evidence for "never runs," independent of
  service reachability.
