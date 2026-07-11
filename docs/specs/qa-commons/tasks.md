# Tasks: qa-commons

- [x] T1: Init repo and lay down the bare Maven skeleton — files: `.gitignore`, `README.md` (stub), `pom.xml` (parent: modules, dependencyManagement, compiler/surefire pluginManagement), `core/pom.xml`, `api/pom.xml`, `template/pom.xml` (empty, inherit parent) — done when: `git init` + this is the first commit; `mvn -q validate` succeeds from repo root with all 3 modules resolving.

- [x] T2: core — `QaConfig` env-based config record — files: `core/pom.xml` (+slf4j), `core/src/main/java/dev/qacommons/core/config/QaConfig.java`, `core/src/test/java/dev/qacommons/core/config/QaConfigTest.java` — done when: `mvn -pl core test` passes; tests cover default values, override via the `fromEnv(Function<String,String>)` seam, and the `QA_BASE_URL` default of `http://localhost:8080`.

- [x] T3: core — `JsonMapperFactory` — files: `core/pom.xml` (+jackson-bom import, databind, jsr310), `core/src/main/java/dev/qacommons/core/json/JsonMapperFactory.java`, test — done when: `mvn -pl core test` passes; a test round-trips a record with an `Instant`/`UUID` field and asserts two `newMapper()` calls return distinct, independently-mutable instances.

- [x] T4: core — `SeededFaker` base + seed logging — files: `core/pom.xml` (+datafaker), `core/src/main/java/dev/qacommons/core/testdata/SeededFaker.java`, test — done when: `mvn -pl core test` passes; test asserts same seed → same generated sequence, different seeds → different sequences, and that the seed is logged at INFO on construction.

- [x] T5: core — AssertJ soft-assertion convention (docs + example, no new prod class) — files: `core/pom.xml` (+assertj-core, compile scope), `core/src/test/java/dev/qacommons/core/conventions/SoftAssertionConventionExampleTest.java`, `core/README.md` — done when: `mvn -pl core test` passes including the example test; README shows the copy-pasteable `@InjectSoftAssertions` pattern.

- [x] T6: api — `ApiResult` sealed hierarchy — files: `api/pom.xml` (+core, assertj), `api/src/main/java/dev/qacommons/api/ApiResult.java`, `api/src/test/java/dev/qacommons/api/ApiResultTest.java` — done when: `mvn -pl api test` passes; covers `Success.expectSuccess()`, `Failure.expectFailure()` throwing when called on the wrong variant, and `Unparsed` construction — no RestAssured involved yet.

- [x] T7: api — `Endpoint` base class + `ResultClassifier`, RestAssured wired in — files: `api/pom.xml` (+rest-assured, allure-rest-assured), `api/src/main/java/dev/qacommons/api/Endpoint.java`, `api/src/main/java/dev/qacommons/api/internal/ResultClassifier.java`, `api/src/main/java/dev/qacommons/api/internal/LoggingFilter.java`, tests against a local `com.sun.net.httpserver.HttpServer` stub — done when: `mvn -pl api test` passes covering get/post/put/delete, path-param substitution, and 2xx/non-2xx/unparseable classification; a scan of `api/src/main/java/dev/qacommons/api/*.java` (excluding `internal/`) contains no `io.restassured` import.

- [x] T8: api — regression test pinning per-instance filter isolation — files: `api/src/test/java/dev/qacommons/api/EndpointFilterWiringTest.java` — done when: `mvn -pl api test` passes; test constructs multiple `Endpoint` instances and asserts each has its own independent filter set (no accumulation, no shared static state).

- [x] T9: template — models, `NotificationsEndpoint`, `FailedNotificationsEndpoint`, `NotificationRequests` factory, duplicate-behavior investigation — files: `template/pom.xml` (+core, api, allure-junit5), `template/src/main/java/dev/qacommons/template/model/*.java`, `template/src/main/java/dev/qacommons/template/api/*.java`, `template/src/main/java/dev/qacommons/template/testdata/NotificationRequests.java` — done when: `mvn -pl template compile` succeeds; real paths/fields probed via the live service's OpenAPI spec + curl and models corrected (create is `POST /api/v1/notifications/send`, no GET-by-id exists, no dedup/409 exists, no `idempotencyKey` field) — findings recorded in plan.md's Risks section.

- [x] T10: template — `junit-platform.properties` + the 4 `@Tag("live")` test scenarios — files: `template/src/test/resources/junit-platform.properties`, `template/src/test/java/dev/qacommons/template/tests/NotificationsTest.java` — done when: with the notification service running locally, `mvn -pl template test -DrunLive=true` passes all 4 tests (send→202 QUEUED; list-failed→200; missing-recipient→400 `VALIDATION_FAILED`; duplicate payload→two 202s with distinct `notificationId`s) on two consecutive runs with no flakes; separately, `mvn -pl template test` (no flag, service NOT running) passes with zero tests executed.

- [x] T11: README — full setup + "run the notification service locally" + live-suite opt-in — files: `README.md` (root), `template/README.md` — done when: following the documented steps top-to-bottom from a fresh clone (start the service, then `mvn -pl template test -DrunLive=true`) reproducibly passes, and the README explicitly documents that plain `mvn clean verify` skips the live suite.

- [x] T12: Full reactor verification — files: none (verification-only; may add a `Makefile`/`justfile` convenience target) — done when: `mvn clean verify` passes end-to-end across all 3 modules with the notification service NOT running (live tests excluded by default); a separate documented `-DrunLive=true` run against the running service also passes.

## Deviations / decisions

Judgment calls made during execution, for review. All were reasoned from the
already-approved plan/amendments and existing skill conventions; flagging
here rather than re-asking per the "stop asking me per-question, batch for
review" instruction given after T10.

- **T2**: pulled `assertj-core` in one task earlier than tasks.md originally
  listed (compile scope from T2, not deferred to T5) so `core`'s own tests
  from T2 onward use AssertJ consistently, matching the architecture skill's
  "AssertJ for all assertions" rather than mixing plain JUnit assertions in
  early tasks and switching later.
- **T7**: dropped query-param support from `Endpoint`'s public verb surface
  (only path-param varargs). Not in the user's original v1 scope ("typed
  verb methods with path params" only), avoids a real Java varargs-overload
  ambiguity risk between a `(String, Object...)` and a
  `(String, Map<String,Object>, Object...)` overload, and nothing in the
  template's 4 scenarios needs it. `FailedNotificationsEndpoint.list()`
  builds its literal `?page=N&size=N` query string directly instead.
- **T9 (major)**: the real service diverged from the plan's working
  assumptions in ways beyond what amendment 2 anticipated — full findings
  and the resulting redesign are recorded in plan.md's Risks section and
  "template test scenarios (final, post-T9-investigation)" section. Two
  divergences were surfaced to the user directly via AskUserQuestion (no
  GET-by-id endpoint at all; no dedup/409 behavior) since they changed two
  of the four originally-named scenarios; resolved as: create/send (202,
  unchanged), list-failed-notifications (replaces get-by-id),
  missing-recipient/400 (unchanged, matched first try), duplicate-produces-
  two-independent-notifications (replaces duplicate/409).
- **T9**: added a second endpoint class, `FailedNotificationsEndpoint`,
  rather than extending `NotificationsEndpoint` — `Endpoint<TReq,TRes,TErr>`
  fixes one response/error type pair per instance, and "send" vs "list
  failed" are genuinely different response shapes off the same base
  resource. Matches "one endpoint class per resource" from the
  endpoint-object-pattern skill.
- **T9**: gitignored `allure-results/` — generated output from T7/T8's
  `allure-rest-assured` filter appearing under `api/` the first time tests
  ran with it wired in; not something to commit.
- **T10**: per-test datafaker seeds are derived as
  `datafakerSeed ^ testMethodName.hashCode()` (via `TestInfo`), not a shared
  `AtomicLong` counter or one reused seed. A shared counter would be static
  mutable state (banned outright); one reused seed across all tests would
  make every test's `NotificationRequests.valid()` draw the *same* fake
  recipient on its first call (identical `Random` seed ⇒ identical first
  draw), which is bad test-data hygiene even though the service doesn't
  currently dedup on it. The XOR-with-test-name derivation stays
  deterministic per test (reproducible from the logged base seed + the
  test's own name) while guaranteeing different tests get different data,
  with zero shared state.
- **T10**: live-suite gating implemented via `<groups>`/`<excludedGroups>`
  Surefire properties (default: exclude `live`) overridden by a `live`
  Maven profile activated by the presence of `-DrunLive=true` — the "Maven
  profile" option from amendment 1's two suggested approaches, over
  `@EnabledIf`.
- **T11**: notification-service startup instructions in
  `template/README.md` use the exact steps the user provided (clone
  `UseYourActive/Notification-Microservice`, `.env.example` → `.env` with
  placeholder credentials, `docker-compose up -d --build`, verify via
  `/q/health/ready`).
