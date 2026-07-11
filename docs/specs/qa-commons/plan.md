# qa-commons v1

## Problem

Personal QA work today is re-solved per-project: HTTP boilerplate leaks into
tests, assertions are hand-rolled, test data is unseeded and flaky, and shared
state (singletons, static RestAssured filters) causes cross-test bleed the
moment tests run in parallel. This has burned the previous framework family
before — deadlocks/bleed from a `TestContext` singleton, and a leaked OAuth
secret from a properties file. qa-commons rebuilds the same working patterns
(Endpoint Objects, typed results, seeded data) as a clean, parallel-safe,
multi-module Maven library, learning from those specific failures.

## Goal / Non-goals

Goals:
- A `core` module with typed env-based config, a Jackson mapper factory, a
  seeded datafaker base factory, and shared AssertJ soft-assertion conventions.
- An `api` module with a generic `Endpoint<TReq, TRes, TErr>` base class over
  RestAssured, a sealed `ApiResult`, typed verb methods with path params, and
  per-instance Allure/logging attachment — RestAssured never visible outside
  the module.
- A `template` module with real, runnable tests against a notification
  service: create (202), get-by-id, duplicate → 409 `NOTIF_081`, and one
  validation-error path — demonstrating both positive and negative flows
  through the typed result, running safely in parallel by default.
- Framework unit tests wherever there's logic (config parsing, `ApiResult`
  classification, factory seeding).
- A README section explaining how to start the notification service locally
  before running the template suite.

Non-goals:
- No `ui`, `perf`, or `mobile` modules yet (added later only when a real need
  arrives, per architecture skill).
- No CI/CD pipeline wiring in this pass — local Maven execution only.
- No test-management (Jira/AIO) sync.
- No auth/token-refresh framework beyond whatever the notification service's
  real API actually requires (keep auth minimal/absent for v1, not
  speculative).
- No DB test-oracle helpers (deferred domain per architecture skill).
- No pluggable/optional Allure abstraction — `api` and `template` depend on
  Allure directly for v1; revisit only if a second, non-Allure consumer shows
  up.
- No Lombok, no MapStruct, no singletons — these are settled, not re-decided
  here.

## Design

### Repo / Maven shape

- Coordinates: parent `groupId=dev.qacommons`, `artifactId=qa-commons`,
  `packaging=pom`, `version=0.1.0-SNAPSHOT`. Modules `core`, `api`, `template`
  → artifacts `qa-commons-core`, `qa-commons-api`, `qa-commons-template`.
- `maven.compiler.release=21` on the parent (no toolchains plugin needed for
  a personal single-JDK setup).
- Parent `dependencyManagement` imports `junit-bom` (5.13.4), `jackson-bom`
  (2.18.2), `allure-bom` (2.29.1), and pins `assertj-core` (3.27.7),
  `rest-assured` (5.5.1 — deliberately staying on the RestAssured 5.x /
  Jackson 2.x line; RestAssured 6.x pulls in Jackson 3, a bigger migration
  than v1 needs), `net.datafaker:datafaker` (2.4.2), `slf4j-api` (2.0.17),
  `logback-classic` (1.5.18).
- `maven-surefire-plugin` (3.5.2, pinned in `pluginManagement`) needs no
  special `<parallel>`/`<forkCount>` config — that's a coarser,
  Surefire-level mechanism, distinct from JUnit Platform's own parallel
  launcher. Parallelism is driven entirely by `junit-platform.properties` on
  the test classpath; do not mix the two mechanisms.
- Dependency scoping: `rest-assured` and `allure-rest-assured` are declared
  **only** in `api`. `core` stays free of RestAssured/Allure entirely so it
  can be reused by future non-API modules (`ui`, `perf`). `logback-classic`
  is declared only in `template` (test/runtime) — `core`/`api` stay
  logging-backend-agnostic via `slf4j-api` alone.

### core (`dev.qacommons.core`)

- `config.QaConfig` — record `(String baseUrl, long datafakerSeed, Duration
  requestTimeout)` with a static `fromEnv()` factory (env vars `QA_BASE_URL`
  default `http://localhost:8080`, `QA_SEED` default current epoch millis
  logged at INFO, `QA_REQUEST_TIMEOUT_MS` default 10000). No caching, no
  static instance — callers construct/pass it explicitly. An overloaded
  `fromEnv(Function<String,String> lookup)` seam makes it unit-testable
  without touching real env vars.
- `json.JsonMapperFactory` — `public final class` with a private constructor
  and a static `newMapper()` method that returns a **fresh** `ObjectMapper`
  per call (JavaTimeModule, `WRITE_DATES_AS_TIMESTAMPS=false`,
  `FAIL_ON_UNKNOWN_PROPERTIES=false`, `NON_NULL` inclusion). `static` is safe
  here because it's a pure factory function with no shared mutable state —
  the banned pattern is a static *field* holding a shared mapper, not a
  static factory *method*.
- `testdata.SeededFaker` — the one allowed base class in `core`; protected
  constructor takes a seed, builds `new Faker(new Random(seed))`, logs the
  seed at INFO once per instantiation. Module-specific factories (e.g.
  `template`'s `NotificationRequests`) extend it and are constructed fresh
  per test — never shared/static — so parallel tests never contend on one
  `Random`.
- Soft-assertion conventions — no new framework code. `core` pulls in
  `assertj-core` (which ships `SoftAssertionsExtension`/
  `@InjectSoftAssertions`) so it's available transitively, and ships one
  passing example test plus a short doc showing the
  `@ExtendWith(SoftAssertionsExtension.class)` + `@InjectSoftAssertions`
  pattern. No hand-rolled base test class — that would be a second base
  class in the module and adds nothing `@ExtendWith` doesn't already give.

### api (`dev.qacommons.api`)

- `ApiResult<T, E>` — sealed interface, `permits Success, Failure, Unparsed`;
  each a record carrying `(int status, Map<String,String> headers, ...)`.
  `Unparsed` also carries the raw body string and the parse `Throwable` (or
  null) for diagnosis. `expectSuccess()`/`expectFailure()` pattern-match and
  throw an `AssertionError` with status + raw body when the shape doesn't
  match. No RestAssured type (`Headers`, `Response`, ...) ever appears in
  this file's signatures.
- Classification lives in one package-private place,
  `internal.ResultClassifier.classify(...)`: 2xx → attempt `successType`
  parse, exception → `Unparsed`; non-2xx → attempt `errorType` parse
  unconditionally, exception → `Unparsed`. No separate "looks like an error
  payload" heuristic — the parse attempt itself is the classifier.
- `Endpoint<TReq, TRes, TErr>` — abstract class, constructor takes
  `(QaConfig config, String basePath, Class<TRes> successType, Class<TErr>
  errorType)`, builds one immutable RestAssured `RequestSpecification` via
  `RequestSpecBuilder` with `AllureRestAssured()` and a small SLF4J-backed
  `internal.LoggingFilter` added **in the constructor** — per instance, never
  static/global. Protected typed verb methods: `get(pathSuffix,
  pathParams...)`, `post(TReq body)`, `post(pathSuffix, body,
  pathParams...)`, `put(...)`, `delete(...)`; query params via an explicit
  `Map<String,Object>` overload, kept separate from positional path-param
  varargs for readable call sites. RestAssured's `RequestSpecification`/
  `Response` types stay private to the verb-method implementations, which
  hand off to `ResultClassifier` before returning — so nothing in `api`'s
  public surface (outside `internal`) imports `io.restassured.*`. Javadoc
  states: immutable spec, but construct one `Endpoint` per test/thread
  rather than sharing an instance across threads.

### template (`dev.qacommons.template`)

- Records: `CreateNotificationRequest(String recipient, String channel,
  String message, String idempotencyKey)`, `NotificationResponse(UUID id,
  String recipient, String channel, String message, String status, Instant
  createdAt)`, `ErrorResponse(String code, String message, List<String>
  details)`. Field names/status codes are a working assumption — Task 9
  confirms them against the real notification service and adjusts before
  tests are written.
- `api.NotificationsEndpoint extends Endpoint<CreateNotificationRequest,
  NotificationResponse, ErrorResponse>` — `create(request)` → `post(request)`
  at `/api/v1/notifications`; `getById(id)` → `get("/{id}", id)`.
- `testdata.NotificationRequests extends SeededFaker` — `valid()`,
  `missingRecipient()`, `withIdempotencyKey(key)`, fluent and named by
  intent.
- **409/idempotency determinism under parallel execution**: no hardcoded
  shared key (that would itself be static shared state). Each test method
  generates its own key via `"dup-test-" + UUID.randomUUID()` as a **local
  variable**, reused for both calls within that one test method — same key
  drives the dedup path within a test, but every test/run gets a fresh key,
  so parallel runs never collide.
- Four tests in `tests.NotificationsTest`, all `@Tag("live")` (see gating
  below):
  - `createNotification_returns202` — create → 202.
  - `getNotificationById_returnsIdentityFields` — create then fetch; asserts
    **identity fields only**: `id`, `recipient`, `channel`, `message`. Never
    asserts `status` — it's timing-dependent (QUEUED/PROCESSING/SENT races
    against the service's own poller), so asserting it would make the test
    flaky by construction, not by bug.
  - `duplicateCreate_...` — **contingent on T9's investigation**, see
    "Duplicate-request contingency" below. Not assumed to be a 409 test.
  - a negative/typed-error path with a non-empty `expectFailure().details()`
    (or equivalent typed error code) — concrete trigger (missing recipient,
    invalid channel, etc.) also confirmed in T9.
- **Duplicate-request contingency**: originally assumed duplicate `create()`
  calls return 409 `NOTIF_081`. The service's actual behavior is understood
  to be a server-side PK conflict producing 409, which duplicate *client*
  requests likely don't trigger — a second identical create is expected to
  return 202 with silent dedup at intake, not 409. T9 verifies this against
  the real, running service before any test is written, then T10
  implements whichever is true:
  - **Dedup confirmed** (second call → 202, single record processed): write
    `duplicateCreate_dedupsSilently` asserting the second response is 202
    and that only one notification exists for that idempotency key (e.g. via
    `getById` or a list/count if the API supports it) — this is still a
    "positive-looking but semantically distinct" flow proving the typed
    result handles it, and does NOT replace the negative/typed-error test.
  - **409 confirmed reachable some other way** (e.g. genuine PK conflict is
    triggerable client-side): keep the `expectFailure().code() ==
    "NOTIF_081"` assertion, using whatever request shape actually produces
    it.
  - Either way, the module still needs at least one negative test hitting a
    genuinely typed 4xx error (invalid channel is the fallback trigger if
    missing-recipient turns out not to error) — the duplicate scenario must
    not be the only negative-shaped test if it turns out to be a 202 path.
  - If reality forces this branch, T9 also gets a short note appended to
    this plan's Risks section (per spec-driven-dev: update the plan, don't
    silently diverge) recording what was actually found.
- **Live-vs-unit gating**: all four scenarios require the real notification
  service and are tagged `@Tag("live")`. `template`'s `pom.xml` sets
  `<excludedGroups>live</excludedGroups>` as a property default, so `mvn
  clean verify` from a fresh clone — with no service running — compiles and
  passes with zero tests executed in `template`. A Maven profile `live`,
  activated by `-DrunLive=true`, clears `excludedGroups` and sets
  `<groups>live</groups>` so only the tagged tests run:
  `mvn -pl template test -DrunLive=true`. README documents starting the
  service first, then this exact command.
- `template/src/test/resources/junit-platform.properties`:
  ```
  junit.jupiter.execution.parallel.enabled=true
  junit.jupiter.execution.parallel.mode.default=concurrent
  junit.jupiter.execution.parallel.mode.classes.default=concurrent
  junit.jupiter.execution.parallel.config.strategy=dynamic
  junit.jupiter.execution.parallel.config.dynamic.factor=1.0
  ```
  `mode.classes.default=concurrent` (not just method-level) is what actually
  stresses `Endpoint`/`SeededFaker` thread-safety, since every test class
  also runs concurrently with every other, not just methods within one
  class. No `@ResourceLock`/`@Execution` overrides needed — every test
  builds its own `Endpoint` and factory instance, no shared fixtures.
- README section: prerequisites + exact steps to start the notification
  service locally, then `mvn -pl template test`.

### Data model

All request/response/error/config types are Java 21 records. No entities, no
DB migrations in this scope.

### Failure modes

- Notification service unreachable at suite start → fails fast via
  `QaConfig.requestTimeout` (RestAssured connection/socket timeout
  configured from it), not a hang.
- Malformed/non-JSON error body on a non-2xx response → `ApiResult.Unparsed`
  instead of a deserialization exception, so tests can still assert on
  status/raw body.
- Duplicate-create races under parallel execution → avoided structurally by
  per-test-local idempotency keys (see above), not by suite-level
  serialization.

## Risks & open questions

- **Real API contract unknown**: field names/paths/status codes above
  (`/api/v1/notifications`, `NOTIF_081`, 202/200/409) are a working
  assumption. Task 9 (scaffolding) and Task 10 (tests) are where this gets
  confirmed and adjusted against the real, locally-running notification
  service — this is expected, not a blocker to starting.
- **Duplicate-request behavior specifically**: per the "Duplicate-request
  contingency" note above, 409 `NOTIF_081` is expected from a server-side PK
  conflict, not from duplicate client posts. T9 investigates and this
  section gets a follow-up note recording the actual finding once known.
- **Auth**: assumed unnecessary for local runs; `Endpoint`'s constructor has
  no auth parameter in v1. If the real service requires it, add a minimal
  pluggable mechanism when that need is confirmed rather than building it
  speculatively now.
- **Allure report generation**: wiring the filter/dependency is in scope;
  actually generating/viewing an Allure HTML report is not part of "done"
  for any task (no Allure CLI assumed installed) — the per-instance filter
  attaching data is the deliverable.
- **api unit tests need an HTTP target**: Task 7 tests `Endpoint` against a
  lightweight local stub rather than a mock framework, to avoid adding a new
  dependency (e.g. WireMock) beyond the agreed list — plan uses a minimal
  `com.sun.net.httpserver.HttpServer`-based stub in `api`'s own test scope.
