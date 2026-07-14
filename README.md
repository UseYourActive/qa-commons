# qa-commons

Personal multi-module test automation framework.

## Requirements

- Java 21
- Maven 3.9+

## Modules

- `core` — typed env-based config, Jackson mapper factory, seeded datafaker
  base factory, shared AssertJ soft-assertion conventions.
- `api` — typed Endpoint Object layer over RestAssured (`Endpoint<TReq, TRes,
  TErr>`, sealed `ApiResult`). RestAssured is an internal implementation
  detail of this module only.
- `ui` — Playwright for Java lifecycle (`PlaywrightExtension`), Page Object
  base, and failure diagnostics (retain-on-failure tracing, screenshot on
  every soft-assertion failure). See `ui/README.md`.
- `db` — Postgres test-oracle helper (`PostgresDatabase`): fail-loud,
  `PreparedStatement`-only DB verification for state the API can't show. No
  dependency on `core` - usable standalone. See `db/README.md`.
- `template` — living example: tests against a notification service proving
  the framework end to end.
- `perf` — Gatling load tests against the notification service. On-demand
  only, see below.

## Build

```
mvn clean verify
```

Runs the full reactor. The `template` and `ui` modules' service-dependent
tests are tagged `@Tag("live")` and excluded by default, so this passes
without any external service running — `ui`'s local-only tests (its
`PlaywrightExtension`/`UiSoftAssertions` lifecycle checks) do launch a real
headless browser, but skip gracefully rather than fail if Chromium isn't
installed yet (see `ui/README.md`). `db`'s self-tests similarly launch a
real ephemeral Postgres via Testcontainers and skip gracefully (not fail) if
Docker isn't available (see `db/README.md`). `perf` compiles as part of the
reactor but never runs here — its `gatling-maven-plugin` has no lifecycle
binding, so a Gatling run only ever happens via an explicit `mvn -pl perf
gatling:test`.

See `template/README.md` for how to run the live suite against a local
notification service, `ui/README.md` for the same plus browser setup and
reading a Playwright trace, `db/README.md` for the DB test-oracle's setup
and self-tests, and `perf/README.md` for how to run the perf sims.

## Reporting

`api` and `template` attach a request/response pair to the report for every
call (per-instance `AllureRestAssured` filter); `ui` attaches a screenshot on
every failure and a trace on every hard failure; every live test attaches
its datafaker seed and target base URL as report parameters
(`ReportContextExtension`). None of this is visible without actually
generating and opening a report - `mvn clean verify`/`test` alone only
produces the raw `allure-results` directories.

Two steps, always in that order:

```
mvn -pl ui,template -am test -DrunLive=true   # run the live suites - populates
                                               # the shared allure-results/ at
                                               # repo root
mvn -N allure:report                          # generate the combined report
```

Then open `target/site/allure-maven-plugin/index.html` in a browser. The
`-N` (non-recursive) flag matters and is not optional: `allure:report` is
bound to Maven's separate `site` lifecycle (never triggered by `clean
verify`), but a *bare* `mvn allure:report` - without `-N` - still walks the
whole reactor and generates a separate, mostly-empty report per module,
exactly the gotcha `gatling:test` taught in the `perf` mission. `-N`
restricts it to the root project, where `allure-results/` (shared by `ui`
and `template` via a Surefire system property) actually lives.

You should see, per test: request/response attachments on `template`'s API
tests, a screenshot/trace attachment on any failing `ui` test, and
`datafakerSeed`/`baseUrl` listed under that test's Parameters.

## Using qa-commons in your project

qa-commons is published via [JitPack](https://jitpack.io), built against
this repo's git tags. Add the JitPack repository:

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>
```

Then depend on whichever module(s) you need. `core`/`api` are the modules
meant to be depended on externally; `template` and `perf` are this repo's
own example/on-demand modules, but JitPack publishes all four the same way:

| Module     | Coordinate                                                              |
|------------|---------------------------------------------------------------------------|
| `core`     | `com.github.UseYourActive.qa-commons:qa-commons-core:v0.1.0`     |
| `api`      | `com.github.UseYourActive.qa-commons:qa-commons-api:v0.1.0`      |
| `template` | `com.github.UseYourActive.qa-commons:qa-commons-template:v0.1.0` |
| `perf`     | `com.github.UseYourActive.qa-commons:qa-commons-perf:v0.1.0`     |

Minimal example — depend on `api` and subclass `Endpoint`:

```xml
<dependency>
  <groupId>com.github.UseYourActive.qa-commons</groupId>
  <artifactId>qa-commons-api</artifactId>
  <version>v0.1.0</version>
</dependency>
```

```java
class WidgetsEndpoint extends Endpoint<Void, WidgetResponse, ErrorResponse> {
    WidgetsEndpoint(QaConfig config) {
        super(config, "/widgets", WidgetResponse.class, ErrorResponse.class);
    }

    ApiResult<WidgetResponse, ErrorResponse> getById(String id) {
        return get("/{id}", id);
    }
}

ApiResult<WidgetResponse, ErrorResponse> result =
        new WidgetsEndpoint(QaConfig.fromEnv()).getById("42");
assertThat(result.expectSuccess().id()).isEqualTo("42");
```

`core` comes in transitively via `api`'s own dependency management; add it
directly only if you need `QaConfig`/`JsonMapperFactory`/`SeededFaker`
without the API layer.

## Versioning

Tags (`vX.Y.Z`, pushed to `origin`) are releases — the only thing a consumer
should ever depend on. Every pom's `X.Y.Z-SNAPSHOT` version is a local-only
development marker: it is never itself a valid consumer coordinate and isn't
expected to numerically track the most recent tag, because JitPack
publishes whatever's at a given tag under that tag's own name regardless of
what the pom says internally.

This "pom never moves" convention is specific to JitPack. If this library
ever moves to Maven Central or GitHub Packages, versioning goes back to real
per-release version bumps in the pom — that's a different publishing
mechanism with different rules, not an extension of this one.

Release tags are cut on `main` only, after merge — never on feature
branches. A tag pushed from a feature branch risks pointing at a commit that
never ends up as an ancestor of `main` (e.g. if that branch is later squash-
merged instead of merged normally), leaving JitPack building and caching a
release against orphaned history.

## Design docs

See `docs/specs/qa-commons/plan.md` and `docs/specs/qa-commons/tasks.md` for
the design and task breakdown behind this framework.
