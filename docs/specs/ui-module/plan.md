# ui module

## Problem

qa-commons has an API layer (`api`) and a load layer (`perf`) but no way to
drive a real browser. Any UI-facing check today would mean a one-off script
outside the framework, duplicating the config/lifecycle/diagnostics plumbing
`core`/`api` already solved, and risking the exact anti-patterns the
`ui-automation-patterns` skill exists to rule out (shared singleton driver
state under parallel execution, tests holding raw locators, failures with no
artifact to look at).

## Goal / Non-goals

Goals:
- A `ui` Maven module, Playwright for Java (latest stable, version verified
  against Maven Central - not assumed), added to the parent reactor.
- A JUnit 5 extension owning the full lifecycle: one `Browser` per JVM
  (expensive - created once, closed at test-plan shutdown), one fresh
  `BrowserContext` + `Page` per test (cheap, isolated - no cookie/storage
  bleed), parallel-safe by construction under the same concurrent JUnit
  config `template` already runs.
- A minimal `BasePage` (one base class, cross-cutting helpers only) and a
  banned old-framework pattern: page objects never accept a `Locator` or
  `Page` from a test method body - only business-named methods.
- Failure diagnostics: per-test Playwright tracing retained on failure, a
  screenshot taken at the moment of *each* soft-assertion failure (via
  AssertJ's own collected-error callback) carrying the assertion message,
  and one screenshot on a hard test failure too (skill's "every failure"
  rule is broader than the mission text's "each soft-assertion failure" -
  both are implemented). Artifacts land under `ui/target/...` with their
  paths printed in the failure output.
- A self-contained proof, inside `ui`'s own `src/test/java` (mirroring how
  `perf` proves itself without touching `template`): page objects for the
  notification service's real Swagger UI (`/q/swagger-ui`) plus 2-3
  `@Tag("live")` tests, gated by the exact same
  `groups`/`excludedGroups`/`-DrunLive=true` pattern already proven in
  `template` (and, this week, ported into the notification service's own
  repo) - not a new gating idiom.
- Headless by default; headed mode via one documented, discoverable flag.
- A README section: browser-binary install (documented **and** automated -
  a `mvn` command, not a manual download), running the live UI suite,
  reading a trace with Playwright's trace viewer.

Non-goals:
- No cross-browser matrix in this pass - Chromium only. Firefox/WebKit are
  reachable via the same install command later; nothing in the design
  blocks it, but proving it isn't this mission's job.
- No visual/pixel regression testing - the skill and the mission both say
  assert contract shape (tags present, an operation's schema tab visible),
  not screenshots-as-assertions. Screenshots here are failure diagnostics
  only, never a pass/fail signal themselves.
- No CI wiring for the live suite, matching `perf`'s and `template`'s own
  precedent - on-demand (`-DrunLive=true`) only.
- No change to `core`, `api`, `template`, or `perf`'s existing code. `ui`
  depends on `core` (for `QaConfig`'s base-URL resolution, reused rather
  than reinvented) but nothing in `core` changes.
- No new hand-rolled soft-assertion wrapper - same rule as everywhere else
  in this repo: build on AssertJ's own `SoftAssertionsExtension`, don't
  replace it. `UiSoftAssertions` (below) is a thin, documented AssertJ
  subclass, not a parallel mechanism.

## Design

### Dependency: Playwright for Java, verified

- `com.microsoft.playwright:playwright:1.61.0` - confirmed as `<latest>`
  and `<release>` in Maven Central's real `maven-metadata.xml` for the
  artifact (not the search index, which lagged behind at 1.52.0 - the
  metadata file is authoritative). Added as a new centralized
  `playwright.version` property + `dependencyManagement` entry in the root
  pom, matching how every other third-party version is pinned.
- `ui/pom.xml` dependencies:
  - `qa-commons-core` (compile) - reuses `QaConfig.fromEnv().baseUrl()` for
    the live suite's navigation target; one source of truth for
    `QA_BASE_URL` across `api`/`perf`/`ui`.
  - `com.microsoft.playwright:playwright` (compile) - needed by
    `src/main` (the extension, `BasePage`), not just tests.
  - `org.junit.jupiter:junit-jupiter-api` (compile, **not** test scope) -
    `PlaywrightExtension` implements JUnit 5 extension interfaces from
    `src/main`, exactly like AssertJ's own `assertj-core` ships
    `SoftAssertionsExtension` in its main jar, not a test jar.
  - `org.assertj:assertj-core` (compile, **not** test scope) - a first for
    this repo (every other module only uses AssertJ in tests); necessary
    because `UiSoftAssertions extends SoftAssertions` lives in `src/main`.
    Called out explicitly here since it's a deliberate deviation from the
    existing test-only-AssertJ convention, not an oversight.
  - `org.slf4j:slf4j-api` (compile) - logging the screenshot/trace paths,
    matching `core`/`api`'s existing logging convention.
  - Test scope: `junit-jupiter` (full, to run tests), `logback-classic`.
    No dependency on `template` - the Swagger UI proof is self-contained
    (DOM interactions, not JSON DTOs; nothing to share with `template`'s
    REST models).
- Root `pom.xml`: `<module>ui</module>` added after `api`, before
  `template` - `ui` depends only on `core`, same dependency depth as `api`.

### Browser binary install - verified against Playwright's current docs

Playwright's Java package ships no browser binaries; they're a separate
download driven by its bundled CLI. Confirmed command (Playwright's own
docs, `docs/browsers`):

```
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install"
```

This requires `exec-maven-plugin` (latest stable, `3.6.3`, confirmed via
Maven Central metadata). Declared in `ui/pom.xml`'s `<build><plugins>`
**with an execution id but no phase binding** - the same "declared, not
bound" idiom already used for `gatling-maven-plugin` in `perf/pom.xml`, so
`mvn clean verify` never triggers a download, but a short documented
command does:

```xml
<plugin>
  <groupId>org.codehaus.mojo</groupId>
  <artifactId>exec-maven-plugin</artifactId>
  <version>${exec-maven-plugin.version}</version>
  <executions>
    <execution>
      <id>install-browsers</id>
      <configuration>
        <mainClass>com.microsoft.playwright.CLI</mainClass>
        <arguments><argument>install</argument></arguments>
      </configuration>
    </execution>
  </executions>
</plugin>
```

Documented in `ui/README.md` as one command: `mvn -pl ui
exec:java@install-browsers`. This is the "automated, not assumed present"
half of the requirement; the "documented" half is the README section
(T-whichever) plus the graceful-skip behavior below for anyone who hasn't
run it yet.

### Lifecycle: `PlaywrightExtension` (`dev.qacommons.ui.PlaywrightExtension`)

Implements `ParameterResolver`, `BeforeEachCallback`, `AfterEachCallback`.
Every method below was checked against the real Playwright 1.61.0 and JUnit
Jupiter 5.13.4 jars (`javap`), not assumed:

- **Browser, once per JVM**: `context.getRoot().getStore(Namespace.GLOBAL)
  .getOrComputeIfAbsent(...)`, storing a `CloseableResource` that wraps both
  the `Playwright` instance and the launched `Browser`
  (`BrowserType.LaunchOptions().setHeadless(...)`). `getOrComputeIfAbsent`
  is JUnit 5's documented thread-safe, create-once primitive - the standard
  idiom for a JVM-scoped resource shared safely across parallel test
  classes; JUnit closes it exactly once, at test-plan shutdown.
- **Context + Page, once per test**: `beforeEach` creates a fresh
  `BrowserContext` and `Page`, stored in the *per-test* `Store` (namespaced
  to that test's own `ExtensionContext` - inherently non-shared under
  parallel execution, no cross-test bleed). Starts tracing immediately:
  `tracing().start(new Tracing.StartOptions().setScreenshots(true)
  .setSnapshots(true).setSources(true))`.
- **`Page` injection**: `ParameterResolver.supportsParameter` matches a
  `com.microsoft.playwright.Page`-typed parameter; tests receive it as a
  method parameter (`void test(Page page)`), not a field - no reflection
  into test instances. This is the one framework primitive tests are
  allowed to touch directly (exactly analogous to `api`'s tests
  constructing `new NotificationsEndpoint(config)` from a framework-issued
  `QaConfig`): tests use it only to construct the top-level page object,
  never to build or pass a `Locator`.
- **Failure detection and trace retention**: `afterEach` reads
  `context.getExecutionException().isPresent()` - populated by JUnit 5
  *before* `AfterEachCallback`s run (confirmed: the interface method is on
  `ExtensionContext` itself, available generally, not gated behind
  `TestWatcher`/`AfterTestExecutionCallback`). Failed → `tracing().stop(new
  Tracing.StopOptions().setPath(...))` under
  `ui/target/playwright-traces/<test-id>.zip`, one more `page.screenshot()`
  saved under `ui/target/playwright-screenshots/`, both paths logged at
  `ERROR`. Passed → `tracing().stop()` (no path - discarded, per
  retain-on-failure). Either way, `browserContext.close()` last.
- **Headed mode**: one env var, `QA_UI_HEADED=true` - same `QA_`-prefixed
  convention `QaConfig` already established (`QA_BASE_URL`, `QA_SEED`),
  read by a small `dev.qacommons.ui.UiConfig.fromEnv()` record local to
  this module. Deliberately **not** added to `core`'s `QaConfig` - a
  browser-headedness flag has nothing to do with API config, and `core`
  doesn't need to know `ui` exists.

### Soft-assertion screenshot hook - verified against the real AssertJ API

Confirmed via `javap` against `assertj-core-3.27.7.jar` (the version this
repo already pins):

- `SoftAssertions` (via `AbstractSoftAssertions` →
  `DefaultAssertionErrorCollector`) exposes
  `addAfterAssertionErrorCollected(AfterAssertionErrorCollected callback)` -
  a real, public, documented extension point, fired synchronously at the
  moment each soft assertion fails (before the test ends). This is the
  literal mechanism the mission asks for.
- **Rejected approach**: reading the collector via
  `SoftAssertionsExtension.getAssertionErrorCollector(ExtensionContext)`
  (AssertJ's own JUnit 5 static helper) and calling
  `addAfterAssertionErrorCollected` on the result. Checked via `javap` and
  it does **not** work - that method returns a package-private
  `ThreadLocalErrorCollector` wrapper that only *delegates*
  `collectAssertionError`; it doesn't expose the add-callback method and
  isn't the real collector instance.
- **Actual design**: `dev.qacommons.ui.UiSoftAssertions extends
  SoftAssertions`, a thin subclass whose public no-arg constructor (AssertJ
  requires one for custom `@InjectSoftAssertions` provider types -
  confirmed via `javap`, `SoftAssertionsExtension` explicitly supports
  subclassing for exactly this) calls
  `addAfterAssertionErrorCollected(error -> onSoftAssertionFailed(error))`.
  Tests declare `@InjectSoftAssertions UiSoftAssertions softly;` instead of
  plain `SoftAssertions`.
- The callback needs the *current test's* `Page` to screenshot, but the
  constructor runs too early to know it (AssertJ constructs the field
  before any `beforeEach` runs). Resolved with a `ThreadLocal<Page>` static
  accessor on `PlaywrightExtension` - the callback lambda reads it lazily,
  only at actual failure time (by which point the test body, and therefore
  `PlaywrightExtension.beforeEach`, has already run). No extension
  ordering dependency between `SoftAssertionsExtension` and
  `PlaywrightExtension` is required - the two are fully decoupled through
  this thread-local, not through field reflection or declaration order.
- **ThreadLocal lifecycle contract, explicit** (amendment): the
  `ThreadLocal<Page>` is owned solely by `PlaywrightExtension` - no other
  class ever writes to it. `beforeEach` calls `set(page)`; `afterEach` calls
  `remove()`, never `set(null)`. This matters specifically because JUnit 5's
  parallel execution runs on a *pooled* worker pool: a set-null still
  leaves a live map entry keyed to that (reused) thread, which is at best a
  leak and at worst stale data a later test on the same pooled thread could
  read if some future code path forgets to `set()` before reading. `remove()`
  actually drops the entry, which is the only correct cleanup for a
  ThreadLocal backing a pooled-thread executor. `PlaywrightExtension` (or a
  small internal holder it owns) carries a one-line Javadoc stating this
  contract - owner, set/remove points, and why `remove()` specifically -
  so it can't be "simplified" back to set-null later by someone who doesn't
  know why that's wrong.
- **Fail loud when unbound** (amendment): if `UiSoftAssertions`'s callback
  fires and finds no `Page` bound for the current thread (`ThreadLocal`
  empty - e.g. `UiSoftAssertions` used outside a `PlaywrightExtension`-managed
  test, or a future refactor breaks the wiring), it does **not** silently
  skip the screenshot. It throws an `IllegalStateException` with an
  actionable message naming both the missing precondition and the fix
  (register `PlaywrightExtension` on the test class). A broken wiring that
  silently produces zero screenshots would be far worse than a loud failure
  - it would look like "no failures ever needed a screenshot" instead of
  "diagnostics are broken."
- Screenshot path: `ui/target/playwright-screenshots/<test-id>-<n>.png`,
  logged at `ERROR` with the assertion's own message, satisfying "attach to
  the report with the assertion message."
- Both together (hard-failure screenshot in `PlaywrightExtension.afterEach`
  + soft-assertion screenshots via `UiSoftAssertions`) implement the
  skill's broader "screenshot on **every** failure" rule, not just the
  mission text's narrower "each soft-assertion failure" phrasing.

### Page Object base and composition

- `dev.qacommons.ui.BasePage` - the one allowed base class. Holds only a
  `protected final Page page` field and a `protected BasePage(Page page)`
  constructor. No generic action methods, no `click(Locator)`-style
  wrapper - the banned old-framework shape.
- Shared fragments (if a future page needs a header/dialog) are composed as
  fields holding another page-object-like class, never inherited - not
  exercised in this pass since the Swagger UI proof needs no fragment
  reuse, but the base class is designed to not get in the way of it later.
- Locator strategy is **role-first**, per the skill, and this was verified
  against the *real* rendered Swagger UI at `/q/swagger-ui` (Quarkus's
  bundled `swagger-ui`), not assumed:
  - Tag section names (e.g. "Notification Delivery") are real `<a>`
    elements with a clean nested `<span>` - `page.getByRole(AriaRole.LINK,
    new Page.GetByRoleOptions().setName(tagName))` resolves to exactly one
    element, no CSS needed.
  - Each operation row's clickable summary is a real `<button
    aria-expanded="...">` - `page.getByRole(AriaRole.BUTTON, new
    Page.GetByRoleOptions().setName(Pattern.compile(...)))` matched on the
    operation's summary text (e.g. "Send a notification") expands it.
  - After expanding, "Schema"/"Example Value" are real `role=tab` elements
    - `page.getByRole(AriaRole.TAB, new
    Page.GetByRoleOptions().setName("Schema"))` proves the schema is
    showing, without depending on any internal CSS class.
  - No CSS/XPath fallback was needed anywhere in this proof - swagger-ui's
    DOM turned out to be fully role-navigable. If a future page object
    needs a CSS/XPath fallback, the skill's rule (last resort, with a
    comment explaining why) applies then.

### Template proof (`ui/src/test/java`, mirrors `perf`'s self-contained pattern)

- `dev.qacommons.ui.pages.SwaggerUiPage extends BasePage` -
  `open(String baseUrl)` (navigates, returns `this`), `endpointTagNames()`
  (read), `operation(String summaryTextFragment)` (returns an
  `OperationRow`).
- `dev.qacommons.ui.pages.OperationRow` - composed fragment, not inherited;
  `expand()` (action, returns `this`), `schemaTabVisible()` (read).
- `dev.qacommons.ui.tests` - 3 `@Tag("live")` tests against the real
  notification service (started via `docker-compose up -d --build`,
  already the established local-run convention from `template/README.md`):
  1. Loading the Swagger UI lists the real tag groups, including
     "Notification Delivery" (contract shape, not a full-list pixel match).
  2. Expanding the "Send a notification" operation reveals its Schema tab.
  3. Expanding the "List failed notifications" operation reveals its
     Schema tab too - two independent operations, not one, to actually
     exercise the page object's reusability rather than one lucky path.
- `ui/src/test/resources/junit-platform.properties` - identical content to
  `template`'s (parallel enabled, concurrent mode/classes, dynamic
  strategy) - the new module adopts the same concurrency stance it's
  required to prove safe under, rather than opting itself out.

### Gating - identical to the established pattern

`ui/pom.xml` gets the exact same `groups`/`excludedGroups` properties +
`maven-surefire-plugin` wiring + `live` profile (`-DrunLive=true`) as
`template/pom.xml` - no new gating idiom invented. `mvn clean verify`
excludes `@Tag("live")` by default; `mvn -pl ui -am test -DrunLive=true`
opts in (`-am` needed the same reason `template`'s README already
documents: `core` isn't installed to the local repo on a fresh clone).

### Resolving the "zero browser tests" constraint

The mission's constraint text ("`mvn clean verify` stays green with the
service down and runs zero browser tests") and its own unit-test guidance
("the extension's lifecycle, the soft-assert screenshot hook - testable
against a local static page or `data:` URL") are in tension read literally:
testing the extension's lifecycle *requires* launching a real (headless)
Chromium instance - that's what makes it a real test of real logic instead
of a mock. Resolved as: **"zero browser tests" means zero tests against the
live external target** (the notification service), which the `@Tag("live")`
gate already guarantees is zero by default. The lifecycle/soft-assert-hook
unit tests do launch a real headless browser, but only against `data:`
URLs - no network dependency, no external service, and no dependency on
whether the notification service is up.

The remaining real risk - a fresh clone with **no Playwright browser
binaries installed yet** would otherwise hard-fail even these local-page
unit tests - is handled with `Assumptions.assumeTrue(...)` at the top of
each: if launching the browser throws Playwright's own "browser executable
doesn't exist" error, the test **skips** (reported, not silently green,
not a hard failure). This mirrors the well-established Testcontainers idiom
of skipping gracefully when Docker isn't available. `mvn clean verify` is
green either way - skipped on a fresh clone, genuinely executed (and still
requiring no external service) once the one-time install command has been
run.

**Loud skips (amendment)**: the `assumeTrue` message is not a generic
"browsers not installed" - it includes the exact, copy-pasteable command
(`mvn -pl ui exec:java@install-browsers`), the same string documented in
the README, so a developer reading a skip in Surefire's summary can act on
it immediately without going to look anything up. `ui/README.md` documents
the complete verification sequence as two explicit steps, in order -
install, then verify - specifically so a run that silently skipped the
browser-dependent unit tests is distinguishable from a run that actually
exercised them: a reader who only ran step 2 and sees `Tests run: N,
Skipped: 2` (or however many) knows immediately that step 1 wasn't done
yet, rather than mistaking a skip for a pass.

### Failure modes

- Browsers not installed → unit tests skip with an actionable message
  (above); live tests fail loudly with Playwright's own clear
  "executable doesn't exist" error (not silently green, not a hang) -
  acceptable since live tests are already opt-in and the README documents
  the install step right next to `-DrunLive=true`.
- Notification service down during a live run → `page.navigate(...)`
  throws / times out per Playwright's own default navigation timeout - the
  test fails loudly, trace + screenshot are captured exactly as for any
  other failure, nothing silently passes.
- A soft-assertion callback itself throws while trying to screenshot (e.g.
  the page has already navigated away) → caught and logged, never allowed
  to mask the original assertion failure it was trying to diagnose.

## Risks & open questions

- **RESOLVED** - "zero browser tests" constraint interpretation, see above.
  Flagging this explicitly rather than picking silently, since it's a
  literal reading of the mission text this plan deliberately departs from.
- **Swagger UI locator strategy was verified against the real, currently
  running notification service** (role-based locators for tag links,
  operation buttons, and schema tabs, confirmed via a scratch Playwright
  probe script, not assumed from generic swagger-ui knowledge) - low risk
  of the proof needing CSS fallbacks later, but a Quarkus/swagger-ui
  version bump could still change the DOM; the tests assert contract shape
  specifically so they degrade to "locator not found" (a clear failure)
  rather than a silent false pass if that happens.
- **`playwright.version` (1.61.0) and `exec-maven-plugin.version` (3.6.3)**
  are independently-versioned artifacts, confirmed via Maven Central
  metadata as of this session; not expected to move in lockstep, matching
  how `gatling.version`/`gatling-maven-plugin.version` are already treated.
  No batching/renovate automation is added to keep them current.
- **Chromium-only for this pass** - open question for a future mission, not
  this one: whether the live suite should ever run Firefox/WebKit too. Not
  blocking; the install command already supports it (`install firefox
  webkit`) whenever it's wanted.
