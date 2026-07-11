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

### template (`dev.qacommons.template`) — REVISED after probing the real service (T9)

The service is a Quarkus app; its OpenAPI spec (`GET /q/openapi?format=json`,
Swagger UI at `/q/swagger-ui`, health at `/q/health/ready`) and direct curl
probes against a locally running instance turned up real behavior that
contradicts the plan's original working assumptions. Per spec-driven-dev,
this section replaces (not appends to) the original template design below.

Confirmed via live probing:
- `POST /api/v1/notifications/send` (not `/api/v1/notifications`) with body
  `{channel, recipient, templateName?, message?, data?}` (`channel` and
  `recipient` required) → **202 Accepted**,
  `{notificationId, status:"QUEUED", message, timestamp, recipient,
  channel}`. `timestamp` is a **`LocalDateTime` with no zone offset**
  (`"2026-07-11T10:54:45.938558279"`), not an `Instant`.
- Missing `recipient` → **400**, `{code:"VALIDATION_FAILED", details:[...],
  message, timestamp}` — a clean typed error; works as the negative/typed-
  error scenario exactly as planned.
- **No GET-by-id endpoint exists anywhere in the spec.** The only read
  endpoints are `GET /api/v1/notifications/failed` (paginated, FAILED-status
  only), `GET /api/v1/channels`, `GET /api/v1/metrics/today`. There is no way
  to fetch a single notification by id.
- **No dedup/409 behavior.** Sending an identical payload (same recipient/
  channel/templateName/data) twice back-to-back returned **two 202s with two
  different `notificationId`s** — no dedup, no 409, no `NOTIF_081` anywhere
  in the spec, despite the endpoint's prose docs claiming a 5-minute dedup
  window. The docs are wrong or the feature isn't wired up; either way, the
  observed behavior is what the test asserts.
- An invalid `channel` enum value (e.g. `"CARRIER_PIGEON"`) causes an
  **unhandled 500 plain-text crash**, not a graceful validation error — ruled
  out as a negative-test candidate (would couple a test to a live bug that's
  expected to get fixed).
- A nonexistent `templateName` is **silently accepted** (202 QUEUED, fails
  later async) — also ruled out as a negative-test candidate for the same
  reason.
- There is **no `idempotencyKey` field** in the real request schema at all;
  the plan's assumed client-supplied dedup key never existed. Dropped from
  the model entirely.

Revised scope, decided with the user after presenting these findings:
1. **Create** — `POST /api/v1/notifications/send` → 202, unchanged in spirit
   from the original plan, just corrected path/fields.
2. **List failed notifications** replaces get-by-id — `GET
   /api/v1/notifications/failed` is the closest thing to a second read path
   that actually exists; different verb/shape (pagination) than create, so
   it's still a meaningfully distinct scenario.
3. **Missing recipient → 400** — unchanged, already matched real behavior.
4. **Duplicate-as-independent-behavior replaces duplicate → 409** — asserts
   the real, verified contract: two identical `create()` calls return two
   *distinct* `notificationId`s, neither erroring. This is honest
   documentation of actual service behavior (not the originally assumed
   dedup/409), is deterministic (no timing/rate-limit dependency), and if the
   service ever gains real idempotency, this test failing is exactly the
   right signal that the contract changed.

Revised data model (`dev.qacommons.template.model`):
```java
public enum NotificationChannel { EMAIL, SMS, TELEGRAM }
public enum NotificationStatus { QUEUED, PROCESSING, SENT, FAILED }

public record CreateNotificationRequest(
    NotificationChannel channel, String recipient, String templateName,
    String message, Map<String, Object> data) {}

public record NotificationResponse(
    String notificationId, NotificationStatus status, String message,
    LocalDateTime timestamp, String recipient, String channel) {}

public record ErrorResponse(String code, String message, List<String> details) {}

public record FailedNotificationSummary(
    String notificationId, String recipient, String channel, String templateName,
    NotificationStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {}

public record FailedNotificationsPage(
    List<FailedNotificationSummary> items, int page, int size, long totalItems, int totalPages) {}
```

`NotificationResponse.channel` stays a plain `String` (matching the schema,
which does not `$ref` the enum for this field) even though
`CreateNotificationRequest.channel` is the typed enum — well-typed on the
way in, liberal on the way out, so an unexpected server value can't turn a
should-be-`Success` into an `Unparsed`.

Two endpoint classes, since `Endpoint<TReq,TRes,TErr>` fixes one
response/error type pair per instance and this resource now has two
genuinely different read shapes (one endpoint class per resource, per the
endpoint-object-pattern skill):
```java
public final class NotificationsEndpoint
        extends Endpoint<CreateNotificationRequest, NotificationResponse, ErrorResponse> {
    public NotificationsEndpoint(QaConfig config) {
        super(config, "/api/v1/notifications/send", NotificationResponse.class, ErrorResponse.class);
    }
    public ApiResult<NotificationResponse, ErrorResponse> send(CreateNotificationRequest request) {
        return post(request);
    }
}

public final class FailedNotificationsEndpoint
        extends Endpoint<Void, FailedNotificationsPage, ErrorResponse> {
    public FailedNotificationsEndpoint(QaConfig config) {
        super(config, "/api/v1/notifications/failed", FailedNotificationsPage.class, ErrorResponse.class);
    }
    public ApiResult<FailedNotificationsPage, ErrorResponse> list(int page, int size) {
        return get("?page=" + page + "&size=" + size);
    }
}
```
`list`'s query string is appended directly as literal text (both params are
`int`s, no encoding risk) rather than extending `Endpoint`'s path-param
surface with query-param support - avoids growing the T7-approved API
surface for a single call site.

`NotificationRequests` factory drops `withIdempotencyKey(...)` (no such
field exists); keeps `valid()` / `missingRecipient()`.

### template test scenarios (final, post-T9-investigation)

Four tests in `tests.NotificationsTest`, all `@Tag("live")` (see gating
below), against the two endpoint classes above:
- `send_returns202Queued` — `send(NotificationRequests.valid())` → 202,
  `expectSuccess().status() == QUEUED`.
- `listFailedNotifications_returns200` — `FailedNotificationsEndpoint.list(0,
  20)` → 200, asserts the page shape (`page`/`size`/`items` non-null); does
  not depend on any specific notification having failed yet.
- `send_missingRecipient_returns400ValidationFailed` — `expectFailure().code()
  == "VALIDATION_FAILED"`, non-empty `details()`.
- `send_duplicatePayload_producesTwoIndependentNotifications` — same
  `CreateNotificationRequest` (one local variable, not a shared/static
  constant) sent twice; asserts both are 202 and their `notificationId`s
  differ — documents the real (no-dedup) contract, deterministic, no
  timing/rate-limit dependency.

No idempotency-key determinism concern remains: there's no client-supplied
key in the real schema, and the duplicate test's only shared state is a
request record built fresh as a local variable per test method (via a
per-test `NotificationRequests` instance), so parallel runs of this test
never collide with each other the same way T2-era `SeededFaker` instances
don't.

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
- Duplicate-create races under parallel execution → avoided structurally:
  each test builds its own request as a local variable via a per-test
  `NotificationRequests` instance, so parallel test methods never share a
  request object or any dedup-relevant state.

## Risks & open questions

- **Real API contract — RESOLVED in T9** by probing the live Quarkus service
  (`/q/openapi?format=json`) and direct curl calls:
  - Create is `POST /api/v1/notifications/send`, not
    `/api/v1/notifications`; success is 202 with `status: QUEUED` and a
    `LocalDateTime` (no zone) `timestamp` field, not `Instant`.
  - **There is no GET-by-id endpoint at all.** Nearest read path is `GET
    /api/v1/notifications/failed` (paginated, FAILED-only) — used as the
    template's second scenario instead.
  - **No dedup/409 exists in practice**, despite the endpoint's prose docs
    claiming a 5-minute dedup window: two identical payloads sent
    back-to-back produced two 202s with two different `notificationId`s.
    No `NOTIF_081` appears anywhere in the spec. The template's duplicate
    scenario now asserts this real behavior instead of a 409.
  - An invalid `channel` value 500s (unhandled exception); a nonexistent
    `templateName` is silently accepted and fails async later. Both ruled
    out as negative-test triggers - the first couples a test to a live bug
    expected to get fixed, the second doesn't produce a synchronous typed
    error to assert on.
  - `idempotencyKey` never existed in the real request schema - dropped
    from the model.
  - See "template test scenarios (final, post-T9-investigation)" above for
    the resulting design.
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
