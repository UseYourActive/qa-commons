# Tasks: ui-module

- [x] T1: scaffold `ui` module, wire into reactor — done: root `pom.xml`
  gets `<module>ui</module>`, `playwright.version` (1.61.0, verified via
  Maven Central's real `maven-metadata.xml`) and `exec-maven-plugin.version`
  (3.6.3) properties + `dependencyManagement` entries; `ui/pom.xml` new,
  with the `groups`/`excludedGroups`/`live` profile mirroring `template`
  and the unbound `install-browsers` execution (confirmed via the plugin's
  own `plugin.xml` that `exec:java` has no default lifecycle phase). `mvn
  clean verify` passed with the empty module; `mvn -pl ui
  exec:java@install-browsers` (scoped to `install chromium` only, per
  plan.md's non-goals) downloaded successfully.

- [x] T2: `BasePage` + `PlaywrightExtension` lifecycle — done, after a real
  architecture correction: the original "one Browser per JVM" design
  produced actual protocol-corruption errors under this repo's own
  concurrent test config (Playwright Java is documented as not
  thread-safe). Fixed to one `Browser`/`Playwright` per worker **thread**
  (matching Playwright's own official JUnit5 extension's sanctioned
  pattern, adapted for correct cleanup under method-level concurrency via
  a root-store registry rather than their per-class `afterAll()`
  teardown) — see plan.md for the full account. Also fixed along the way:
  browsers-missing now skips gracefully and fast (was silently
  auto-downloading for 3 minutes without `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`);
  `afterEach` null-guards for a `beforeEach` that aborted via
  `Assumptions.assumeTrue`. `ThreadLocal<Page>` ownership contract
  (owned solely by this class, `set`/`remove` not set-null) implemented
  and documented in the class Javadoc per the approved amendment. 11
  tests in `PlaywrightExtensionTest`, all passing, including 8
  `@RepeatedTest` repetitions that would flake under real concurrency if
  the isolation were ever broken again.

- [x] T3: `UiSoftAssertions` soft-assertion screenshot hook — done:
  `addAfterAssertionErrorCollected` fires the screenshot at the moment of
  failure, reading the current `Page` from `PlaywrightExtension`'s
  `ThreadLocal` lazily. Fails loud (`IllegalStateException`, actionable
  message) when unbound — verified this actually propagates out of the
  `assertThat(...)` call itself. Refactored the artifact-path logic to be
  reusable without an `ExtensionContext` (a second `ThreadLocal` for the
  current test id) and fixed a latent filename-collision bug found in the
  process (repeated tests sharing one test id need a per-artifact
  sequence number, not just the id).

- [x] T4: hard-failure screenshot + trace retention wiring — done: the
  capture logic itself was already written into `afterEach`'s failure
  branch as part of T2; this task supplied the missing proof.
  `PlaywrightExtensionFailureDiagnosticsTest` uses
  `junit-platform-launcher` to programmatically run a deliberately-failing
  nested class and a passing one, then inspects what actually landed on
  disk — trace+screenshot for the failing case, nothing for the passing
  one.

- [x] T5: `SwaggerUiPage` + `OperationRow` page objects — done: role-first
  locators throughout (`getByRole(LINK/BUTTON/TAB, ...)`), verified
  against the real running Swagger UI via a scratch Playwright probe
  before writing any page-object code — zero CSS/XPath needed anywhere.
  `hasEndpointGroup(String)` replaced an earlier "list all tags" sketch
  (simpler, still purely role-based). Neither class exposes a method
  accepting `Locator`/`Page` from a caller.

- [x] T6: live Swagger UI tests — done: 3 `@Tag("live")` tests (endpoint-
  group presence, schema-tab visibility for two independent operations).
  Ran for real both ways: passes with the service up (~3.4s), fails loud
  in ~6s (`net::ERR_CONNECTION_REFUSED`, not a hang) with it down; zero
  live tests run under default `mvn clean verify` either way. Fixed
  `operation()`'s locator en route: Playwright's Java bindings don't
  extend `setName(Pattern)` substring matching the way the JS bindings do
  (confirmed via a side-by-side probe, 0 vs 1 matches on the same button)
  — switched to the `String` overload, which does substring-match by
  default.

- [x] T7: headless/headed flag — done: the wiring existed from T2; this
  task added real unit coverage for `UiConfig` (previously untested) and
  verified end-to-end against the live service both ways — default
  headless passes in ~3.4s, `QA_UI_HEADED=true` launches a real visible
  window and passes in ~5.4s (measurably slower, consistent with genuine
  window rendering). Headed mode surfaced a real correctness bug in the
  page objects, not just a timing quirk: `hasEndpointGroup`/
  `schemaTabVisible` used `count()`/`isVisible()` (one-shot, non-retrying)
  instead of `waitFor()` (Playwright's real auto-waiting primitive) —
  passed in headless mode by lucky timing, failed under headed mode's
  slower rendering. Fixed both. Also fixed the T4-style artifact-count
  fragility recurring in two more test classes under repeated `mvn test`
  runs without `clean`.

- [x] T8: README — done: root `README.md` gets a `ui` module-list entry +
  a Build-section note on its browser-dependent-but-graceful-skip local
  tests. New `ui/README.md`: install command, `-DrunLive=true` usage
  mirroring `template/README.md`, headed-mode flag, the two-step
  "complete verification sequence" (install, then verify) with the
  `Skipped`-count note, the exact trace-viewer command, and a short
  page-object-writing guide capturing the `waitFor()`-not-`count()`
  finding from T7. Did not add a `ui` row to the existing JitPack
  coordinate table in the root README — it lists what's real at the
  `v0.1.0` tag, which predates this module.

- [x] T9: full reactor + safety re-verification — done: fresh
  `mvn clean verify` from repo root with the notification service
  genuinely stopped (`docker-compose down`) — `BUILD SUCCESS` across all
  6 reactor entries including `ui` (19 tests, 0 failures, 0 live tests),
  `qa-commons-template` still `Tests run: 0`. Service restarted and
  confirmed healthy; `mvn -pl ui -am test -DrunLive=true` — 3/3 passing.
  Every checkbox in this file is now `[x]`.
