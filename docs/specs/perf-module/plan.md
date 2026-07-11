# perf module

## Problem

qa-commons has no way to answer "does this hold up under load" — only
functional pass/fail via `api`/`template`. Load testing today would mean a
one-off script outside the framework, duplicating the base-URL/model/config
plumbing `core`/`api`/`template` already solved, and risking exactly the kind
of silent-wrong-target mistake (hitting an unintended host at load) that's
far more damaging than a functional test doing the same thing.

## Goal / Non-goals

Goals:
- A `perf` Maven module, Gatling Java DSL, wired into the parent reactor for
  compilation but never executed by `mvn clean verify` — perf runs are
  on-demand only.
- A protocol layer that resolves the target base URL from the same
  `QA_BASE_URL` env var `core`/`template` already use, and **refuses to
  build** (throws, before any load is injected) if that var is unset and no
  localhost default is actually reachable — no silent default that could
  point at an unintended host.
- A send-notification step + scenario for the notification service, built
  from `template`'s existing request/response models — one source of truth
  for request shapes, not a parallel copy.
- Per-virtual-user test data via a Gatling feeder backed by `core`'s
  `SeededFaker`, so no two virtual users share one mutable model instance.
- Two simulations: a short, low-rate smoke sim with pass/fail assertions
  (the "is it wired right" check), and a manual-only ramping sim for finding
  the breaking point.
- A README section: how to run each sim, how to read the Gatling HTML
  report, and an explicit warning that perf runs leave real rows in the
  target service's database.

Non-goals:
- No CI wiring for perf runs, now or as a follow-up in this pass — on-demand
  (`mvn -pl perf gatling:test`) only, per explicit instruction.
- No DB cleanup tooling for rows perf runs create — documented as an
  accepted cost in the README, not automated. Building a cleanup mechanism
  would need DB access qa-commons doesn't have and nothing in scope asks for.
- No load coverage beyond the send-notification path (e.g. no perf scenario
  for `GET /api/v1/notifications/failed`) — v1 is one realistic journey, not
  full endpoint coverage.
- No Gatling Enterprise/cloud reporting integration — local HTML report only.
- No new config mechanism — reuses `QA_BASE_URL`, doesn't invent a
  perf-specific env var or properties file.
- No `qa-commons-api`/RestAssured dependency — Gatling has its own async
  HTTP client; pulling in `api` would add an unused, misleading dependency.

## Design

### Repo / Maven shape

- New module `perf` → artifact `qa-commons-perf`, packaging `jar` (Gatling's
  Maven plugin expects a normal test-classpath jar module, matching
  Gatling's own official Java Maven demo layout). Added to the parent's
  `<modules>` last.
- Parent `pom.xml` gets two new centralized version properties —
  `gatling.version` (`3.15.1`, latest `gatling-charts-highcharts` on Maven
  Central as of 2026-07) and `gatling-maven-plugin.version` (`4.21.8`, latest
  plugin release, same date) — plus a `dependencyManagement` entry for
  `io.gatling.highcharts:gatling-charts-highcharts` and a `pluginManagement`
  entry for `io.gatling:gatling-maven-plugin`, matching how every other
  third-party version is already centralized.
- `perf/pom.xml` depends on `qa-commons-core` and `qa-commons-template`
  (both `test` scope — every perf source file lives under `src/test/java`,
  matching Gatling's own convention) for `QA_BASE_URL`-style config
  precedent, `JsonMapperFactory`, `SeededFaker`, and the notification
  request/response models; `io.gatling.highcharts:gatling-charts-highcharts`
  (`test` scope) for the DSL + engine + HTML reporter; `logback-classic`
  (`test` scope) so Gatling's own logging is controlled the same way
  `template` controls it. `junit-jupiter` + `assertj-core` (`test` scope)
  are added **only** for the one narrow protocol safety-net test — see Risks.
  No `qa-commons-api` dependency (Non-goals).
- `gatling-maven-plugin` is declared in `perf/pom.xml`'s `<build><plugins>`
  with **no `<executions>` block** — this is the actual safety mechanism,
  not a comment. Gatling's own Maven docs are explicit that the `test` goal
  only binds to the `integration-test` phase (and therefore fires on `mvn
  verify`) when an `<executions>` entry declares that binding; omitting it
  entirely (confirmed against Gatling's official
  `gatling-maven-plugin-demo-java` reference pom) leaves the goal reachable
  **only** via an explicit `mvn gatling:test` / `mvn -pl perf gatling:test`
  invocation. `mvn clean verify` from the repo root still compiles
  `perf`'s test sources (harmless) but never triggers a Gatling run.
- **Verified during T1**: `gatling:test` is a directly-invoked Mojo, not a
  lifecycle-phase goal — unlike `mvn -pl template -am test` (which works
  fine because bound lifecycle goals are scoped per-project automatically),
  `mvn -pl perf -am gatling:test` actually applies the goal to *every*
  project the reactor resolves (including the `pom`-packaging root
  aggregator, which has no test output and always fails with "No
  simulations to run"), aborting before it ever reaches `perf`. The correct
  two-step pattern is: install dependencies once via a real lifecycle phase
  where `-am` behaves normally (`mvn install -pl perf -am -DskipTests`),
  then invoke `gatling:test` scoped to `perf` alone, **no `-am`**
  (`mvn -pl perf gatling:test -Dgatling.simulationClass=...`). Every command
  in this plan and in `perf/README.md` uses that two-step pattern.
- Simulation classes are named `*Simulation.java` (`SmokeSimulation`,
  `FindNotificationLimitSimulation`), never `*Test.java`/`*Tests.java` —
  Surefire's default include glob (`**/*Test.java`, `**/Test*.java`,
  `**/*Tests.java`, `**/*TestCase.java`) doesn't match that pattern, so the
  ordinary `test` phase (which parent's `pluginManagement` already pins for
  every module) finds zero test classes to run in `perf` beyond the one
  intentional unit test from T2. This is a second, independent guard on top
  of the missing `<executions>` block — no `<skip>` flag needed, and no
  ambiguity about "why didn't Surefire pick these up."
- `perf/src/test/resources/gatling.conf` and `logback-test.xml` — the
  standard Gatling config/log locations for a Maven project, copied in
  structure (not content) from Gatling's official demo.

### protocol (`dev.qacommons.perf.protocol`)

- `NotificationServiceProtocol` — one class, one static factory
  `httpProtocol()` returning an `HttpProtocolBuilder`
  (`baseUrl(...).acceptHeader("application/json").contentTypeHeader("application/json")`).
- Base URL resolution (mirrors `QaConfig`'s `fromEnv(Function<String,String>)`
  testability seam, but with different, stricter behavior on "unset"):
  1. `QA_BASE_URL` set and non-blank → use it verbatim. An explicit value is
     trusted; no reachability probe.
  2. `QA_BASE_URL` unset/blank → attempt a short (500 ms) TCP connect to
     `localhost:8080` (the same default host/port `QaConfig` silently
     assumes today). Reachable → use `http://localhost:8080`, logging a
     `WARN` that no `QA_BASE_URL` was set and this is an unverified,
     implicit default someone should confirm is actually the right target.
     Unreachable → throw `IllegalStateException` with a message naming both
     missing conditions (env var unset, localhost not listening) — Gatling's
     `setUp()` never runs, no load is ever injected against a guessed host.
  3. A package-private `resolveBaseUrl(...)` overload takes the env lookup
     function and the reachability probe as explicit parameters (same seam
     pattern as `QaConfig`), so the branching logic is unit-testable without
     a real socket or real env var.
- **Verified during T2**: `httpProtocol()`/`httpProtocol(lookup, probe)`
  themselves are *not* unit-testable - touching Gatling's `http` DSL
  singleton outside a Gatling-run `Simulation` throws `IllegalStateException:
  Simulations can't be instantiated directly but only by Gatling` from
  `HttpDsl`'s own static initializer. `NotificationServiceProtocolTest`
  therefore covers `resolveBaseUrl(...)` only (pure logic, no Gatling
  types touched) - exactly the "lookup/probe seam" scope the Risks section
  called for, not a broader scope creep. `httpProtocol()`'s wiring of that
  resolved URL into an actual `HttpProtocolBuilder` is exercised for real
  by `SmokeSimulation` in T6.
- Deliberately does **not** reuse `QaConfig.fromEnv()` directly: that method
  always substitutes `DEFAULT_BASE_URL` when the env var is unset and gives
  the caller no way to distinguish "explicitly configured" from "defaulted,"
  which is exactly the silent-default risk this module is required to rule
  out. `perf` does its own resolution instead of changing `QaConfig`'s
  contract for every existing consumer.

### steps (`dev.qacommons.perf.steps`)

- `NotificationSteps.sendNotification()` — a `ChainBuilder`:
  ```java
  exec(http("Send Notification")
      .post("/api/v1/notifications/send")
      .body(StringBody(session -> mapper.writeValueAsString(
          session.get("notificationRequest"))))
      .asJson()
      .check(status().is(202), jmesPath("notificationId").exists()));
  ```
  `mapper` is `core`'s `JsonMapperFactory.newMapper()` (one Jackson config
  for the whole framework, same as `api`'s `Endpoint`) — not a hand-rolled
  serializer. The session variable `notificationRequest` holds a
  `dev.qacommons.template.model.CreateNotificationRequest` produced by the
  feeder (below); the step never constructs or mutates a request itself,
  keeping all per-user state flowing through the Session per Gatling
  convention.

### testdata + scenario (`dev.qacommons.perf.testdata`, `.scenarios`)

- `NotificationFeeders.perUser(long seed)` — returns
  `Iterator<Map<String,Object>>`, built as
  `Stream.generate(() -> Map.of("notificationRequest", requests.valid())).iterator()`
  over one `NotificationRequests` (extends `core`'s `SeededFaker`) instance
  constructed once from the given seed. Gatling's `feed()` step synchronizes
  iterator access across virtual users itself (a feeder is designed to be
  pulled concurrently); what this module guarantees on its own side is that
  **every pull produces a freshly-built `CreateNotificationRequest`** — no
  virtual user ever reads or mutates a record another VU is also holding.
- `NotificationScenarios.sendNotificationJourney()` —
  `scenario("Send Notification").feed(NotificationFeeders.perUser(seed)).pause(100.millis(), 400.millis()).exec(NotificationSteps.sendNotification())`.
  The small randomized pause before the call is the "realistic" part of the
  journey — real clients don't fire requests in perfect lockstep; without it
  every virtual user's first request would land in the same scheduler tick.

### Rate limiting & delivery amplification — added after plan approval, before execution (amendment)

Investigated directly against the target's source (a local clone of
`UseYourActive/Notification-Microservice` at
`bg.sit_varna.sit.si.service.redis.RateLimitService` /
`.service.async.NotificationProcessor`, not just black-box probing):

- **Rate limiting is keyed per `(recipient, channel)`, not global** — Redis
  `INCR`+`EXPIRE` under `rate-limit:<channel>:<recipient>`. Defaults: EMAIL
  10/hour, SMS 5/hour, TELEGRAM 20/hour, fail-open on Redis errors. Exceeding
  it returns **`429`** with a `Retry-After` header **synchronously from
  `POST /api/v1/notifications/send`** (checked at intake, before 202) — not
  an async/later failure. The shipped `.env`/`.env.example` sets
  `REDIS_RATE_LIMIT_ENABLED=false` — **disabled by default** on a fresh
  local setup; a shared/staging perf target may have it enabled.
- **Decision: no design change needed, because the sims are already
  rate-limit-safe by construction.** `NotificationFeeders.perUser` (above)
  draws a fresh, effectively-unique recipient from `NotificationRequests`
  for every single request — since the limiter's key includes the
  recipient, and recipients are never reused/pooled across requests, neither
  sim can plausibly accumulate 10+ hits against the same `(recipient,
  channel)` key regardless of throughput. This sidesteps "pick the loosest
  channel" or "raise the limit for perf runs" entirely — there was no
  channel-choice or config-loosening decision to make once the key shape
  was understood. `FindNotificationLimitSimulation` therefore genuinely
  measures **queue/intake capacity** (the Postgres-backed queue,
  `WORKER_CONCURRENCY=20`, `POLL_BATCH_SIZE=20`, `POLL_INTERVAL=1s`), not
  the rate limiter, satisfying the amendment's requirement directly.
- **Guard rail, per instruction**: if a `429` is ever observed in a run
  (smoke or find-the-limit), that is a signal something violated the above
  assumption — e.g. a feeder regression reusing recipients, or a perf
  target with non-default (smaller) limits — and must be investigated as a
  bug, never silently absorbed by loosening an assertion to make the run
  green. Neither simulation adds explicit `429` tolerance.
- **Delivery amplification with placeholder credentials** (`RETRY_MAX_ATTEMPTS`,
  `NotificationProcessor` `@Retry`/`@Fallback`): every notification's async
  delivery will fail against the real provider (SendGrid/Twilio/Telegram)
  with placeholder credentials. Layer 1: `@Retry(maxRetries=3)` on
  `processNotification` → **4 total processing attempts**, ~2s apart
  (`RETRY_DELAY=2000`). Layer 2: `@Fallback` marks the row `FAILED` and
  reschedules it on a Redis-backed cold queue **5 minutes later**, for up to
  `MAX_COLD_RETRY_CYCLES=5` more cycles — real backend work continuing for
  20+ minutes after a perf run's virtual users have all finished. This
  matches the amendment's "~4 provider attempts plus cold-queue churn"
  exactly and must be stated in `perf/README.md`, not just known
  informally.

### simulations (`dev.qacommons.perf.simulations`)

- `SmokeSimulation` — `constantUsersPerSec(2).during(30.seconds())` (open
  model, matching the skill's default for internet-facing services),
  assertions:
  ```java
  .assertions(
      global().successfulRequests().percent().gte(99.0),
      global().responseTime().percentile(95).lt(800));
  ```
  Purpose: "is the perf module wired correctly end-to-end", run after every
  change to protocol/steps/scenario and whenever the target service changes.
  Not rate-limit-sensitive per the amendment above — success-rate/p95
  reflect intake + rate-limiter-check latency only, never 429s under normal
  operation.
- `FindNotificationLimitSimulation` — `rampUsersPerSec(1).to(50).during(5.minutes())`,
  class-level Javadoc states MANUAL-ONLY, never scheduled/automated, that it
  measures queue/intake capacity (not the rate limiter, per the amendment
  above), and that hitting the target's real limit is the point (some
  requests are *expected* to fail near the top of the ramp). Still carries
  one intentionally loose assertion —
  `global().successfulRequests().percent().gt(0.0)` — so a completely dead
  target produces a visibly failed Gatling run rather than a deceptively
  "green" report with zero real traffic; it is not a pass/fail gate the way
  the smoke sim's assertions are.

### README

- Root `README.md`: add `perf` to the module list and one line in the Build
  section stating `mvn clean verify` never runs perf (mirroring the existing
  `template` live-suite note).
- New `perf/README.md`: prerequisites (notification service running, same
  as `template/README.md`; **and** `core`/`api`/`template`/`perf` installed
  to the local repo once via `mvn install -pl perf -am -DskipTests` — see
  the note below on why `-am` can't be combined with the `gatling:test`
  invocation itself), exact commands for both sims
  (`mvn -pl perf gatling:test -Dgatling.simulationClass=dev.qacommons.perf.simulations.SmokeSimulation`
  and the find-the-limit equivalent, labeled manual-only), how to read the
  generated `perf/target/gatling/<sim>-<timestamp>/index.html` report
  (global stats, response-time percentile chart, requests/sec chart, the
  assertions pass/fail banner at the top), and an explicit warning that
  every run leaves real `Notification` rows in the target service's
  database (no dedup, confirmed in `qa-commons`'s own T9 investigation) —
  documented as an accepted cost of perf testing against a real backend, not
  something this module cleans up. Additionally, per amendment, states:
  (a) perf runs **require placeholder provider credentials** in the
  target's `.env` (Telegram/Twilio/SendGrid) — real credentials would spam
  real channels at load rates; (b) with placeholders, every notification
  fails delivery and amplifies into ~4 in-memory processing attempts
  (~2s apart) plus up to 5 further Redis cold-queue retry cycles (5 minutes
  apart) — the target does real work for 20+ minutes after a run ends, well
  beyond the injected request rate, which is accepted as realistic
  backpressure but must be understood when reading a report (e.g. "requests
  actually reaching workers" isn't visible in Gatling's own client-side
  numbers).

### Failure modes

- Target unreachable / `QA_BASE_URL` unset with nothing on localhost:8080 →
  `NotificationServiceProtocol.httpProtocol()` throws during Gatling's
  `setUp()`, before a single virtual user is injected — fails fast and
  loud, not a hang or a misdirected load test.
- Individual request timeout/connection-refused mid-run → surfaced as a
  failed check on that request by Gatling's own HTTP client; aggregated
  into the success-rate assertion, not a simulation crash.
- Perf runs accumulate real rows in the target service (confirmed no dedup
  exists) → accepted and documented, not remediated in this module
  (Non-goals).

## Risks & open questions

- **RESOLVED — protocol safety-guard unit test.** Confirmed: keep the one
  narrow `NotificationServiceProtocolTest` in T2, per approval.
- **RESOLVED (amendment) — rate limiting.** See "Rate limiting & delivery
  amplification" in Design above: investigated against the target's actual
  source, no sim design change needed because per-recipient keying +
  per-request-unique recipients make both sims structurally rate-limit-safe.
  If this assumption ever proves wrong (a `429` shows up in a real run), stop
  and investigate rather than loosening an assertion — per explicit
  instruction.
- **Smoke sim thresholds (2 req/s, 30 s, p95 < 800 ms, ≥99% success) are
  placeholders** — the notification service's actual capacity is unknown
  (no perf baseline exists yet). These are deliberately conservative
  "is it wired right" numbers, not a real SLO. Expect to tune them after
  the first real smoke run in T6.
- **`gatling.version` (3.15.1) and `gatling-maven-plugin.version` (4.21.8)**
  are independently-versioned artifacts (confirmed via Maven Central
  metadata, 2026-07); they're not expected to match numerically and this
  plan does not try to force them to.
