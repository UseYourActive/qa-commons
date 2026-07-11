# Tasks: perf-module

- [ ] T1: Scaffold `perf` module, wire into the reactor, prove it's execution-safe — files: `pom.xml` (root: `+module`, `gatling.version=3.15.1`, `gatling-maven-plugin.version=4.21.8` properties, dependencyManagement entry for `io.gatling.highcharts:gatling-charts-highcharts`, pluginManagement entry for `io.gatling:gatling-maven-plugin`), `perf/pom.xml` (deps: `qa-commons-core`, `qa-commons-template`, `gatling-charts-highcharts`, `logback-classic`, `junit-jupiter`, `assertj-core` — all `test` scope; `gatling-maven-plugin` declared in `<build><plugins>` with no `<executions>`), `perf/src/test/resources/gatling.conf`, `perf/src/test/resources/logback-test.xml` — done when: `mvn clean verify` from repo root is green with 4 modules in the reactor and the build log shows no Gatling simulation run; `mvn -pl perf -am gatling:test` (no simulation classes exist yet) fails with Gatling's "no simulations to run" message, proving the plugin is reachable on demand and not auto-bound.

- [ ] T2: protocol — `NotificationServiceProtocol` safety-guard + unit test — files: `perf/src/test/java/dev/qacommons/perf/protocol/NotificationServiceProtocol.java`, `perf/src/test/java/dev/qacommons/perf/protocol/NotificationServiceProtocolTest.java` — done when: `mvn -pl perf test` passes, covering (a) `QA_BASE_URL` set → used verbatim, no probe called, (b) unset + probe reports reachable → `http://localhost:8080` used, WARN logged, (c) unset + probe reports unreachable → `IllegalStateException` naming both conditions; `mvn clean verify` from root stays green.

- [ ] T3: steps — `NotificationSteps.sendNotification()` reusing template's models — files: `perf/src/test/java/dev/qacommons/perf/steps/NotificationSteps.java` — done when: `mvn -pl perf test-compile` succeeds; the file's only `dev.qacommons.template.*` imports are `CreateNotificationRequest`/model types (no duplicated record), body serialization goes through `core`'s `JsonMapperFactory.newMapper()`, and the check asserts `status().is(202)` + `jmesPath("notificationId").exists()`.

- [ ] T4: feeder + scenario — files: `perf/src/test/java/dev/qacommons/perf/testdata/NotificationFeeders.java`, `perf/src/test/java/dev/qacommons/perf/scenarios/NotificationScenarios.java` — done when: `mvn -pl perf test-compile` succeeds; `NotificationFeeders.perUser(seed)` wraps one `NotificationRequests` (extends `SeededFaker`) instance and returns a lazily-generated `Iterator<Map<String,Object>>` (no pre-materialized list, no static/shared feeder field); `NotificationScenarios.sendNotificationJourney()` composes `feed(...).pause(100.millis(), 400.millis()).exec(NotificationSteps.sendNotification())`.

- [ ] T5: Rate-limit & delivery-amplification investigation, design decision, README draft — files: `docs/specs/perf-module/plan.md` (already amended), `docs/specs/perf-module/tasks.md` (this Deviations log), `perf/README.md` (new file — draft the rate-limit and credentials/amplification subsections now; T8 fills in the rest) — done when: `perf/README.md` states (a) the rate limiter is keyed per `(recipient, channel)` not global, the actual default limits (EMAIL 10/h, SMS 5/h, TELEGRAM 20/h), and that `.env.example` ships it disabled; (b) why neither sim needs 429 tolerance (per-request-unique recipients structurally avoid the limiter, confirmed against the target's source) and that a real 429 must be investigated, never tolerated by loosening an assertion; (c) placeholder credentials are required (real ones would spam real channels at load rates) and cause ~4 in-memory processing attempts (~2s apart) plus up to 5 Redis cold-queue retry cycles (5 min apart) per notification — real backend work continuing 20+ minutes past a run's end.

- [ ] T6: `SmokeSimulation` — files: `perf/src/test/java/dev/qacommons/perf/simulations/SmokeSimulation.java` — done when: with the notification service running locally, `mvn -pl perf -am gatling:test -Dgatling.simulationClass=dev.qacommons.perf.simulations.SmokeSimulation` completes, produces an HTML report under `perf/target/gatling/`, both assertions (`successfulRequests().percent().gte(99.0)`, `responseTime().percentile(95).lt(800)`) pass, and the report shows zero `429`s (confirming the T5 rate-limit-safety design decision held in practice); `mvn clean verify` from root (service not running) stays green and doesn't touch this class.

- [ ] T7: `FindNotificationLimitSimulation` — files: `perf/src/test/java/dev/qacommons/perf/simulations/FindNotificationLimitSimulation.java` — done when: `mvn -pl perf test-compile` succeeds; class Javadoc states MANUAL-ONLY, that it measures queue/intake capacity (not the rate limiter, per T5), and that failures near the top of the ramp are expected; a capped manual run (e.g. `-Dgatling.simulationClass=...FindNotificationLimitSimulation` against the running service, or a temporarily shortened `.during()` for the trial run) completes and produces a report with the single loose sanity assertion visible in the summary; any `429` observed is treated as a bug and investigated before this task is marked done, not assertion-suppressed.

- [ ] T8: README — perf section — files: `README.md` (root: `+perf` module bullet, one-line "excluded from `mvn clean verify`" note in Build), `perf/README.md` (finish: prerequisites, exact `gatling:test` commands for both sims with the find-the-limit one labeled manual-only, how to read `perf/target/gatling/<sim>-<timestamp>/index.html`, the DB-rows-accumulate warning/acceptance note, on top of T5's rate-limit/amplification subsections) — done when: following the README commands verbatim against the running service reproduces T6's and T7's results.

- [ ] T9: Full reactor + safety re-verification — files: none (verification-only) — done when: `mvn clean verify` passes end-to-end across all 4 modules with the notification service NOT running, and nothing under `perf/target/gatling/` is produced by that run; separately, the two documented on-demand `gatling:test` commands against the running service both pass, confirming the module works exactly as README'd.

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
