# Tasks: ui-module

- [ ] T1: scaffold `ui` module, wire into reactor — files: `pom.xml` (root,
  add `<module>ui</module>`, `playwright.version`/`exec-maven-plugin.version`
  properties, `dependencyManagement` entries for
  `com.microsoft.playwright:playwright` and `qa-commons-ui`), `ui/pom.xml`
  (new — dependencies per plan.md, `exec-maven-plugin` with the unbound
  `install-browsers` execution, `groups`/`excludedGroups` properties +
  `live` profile mirroring `template/pom.xml`) — done when: `mvn clean
  verify` from repo root still succeeds with zero `ui` source files yet
  (empty module compiles), and `mvn -pl ui exec:java@install-browsers`
  downloads Chromium successfully.

- [ ] T2: `BasePage` + `PlaywrightExtension` lifecycle — files:
  `ui/src/main/java/dev/qacommons/ui/BasePage.java`,
  `ui/src/main/java/dev/qacommons/ui/PlaywrightExtension.java`,
  `ui/src/main/java/dev/qacommons/ui/UiConfig.java` — done when: the
  extension compiles against the verified APIs in plan.md (JVM-scoped
  `Browser` via root store + `CloseableResource`, per-test `BrowserContext`
  + `Page` via per-test store, `Page` injection via `ParameterResolver`,
  tracing start/stop with retain-on-failure via
  `getExecutionException()`); the `ThreadLocal<Page>` is owned solely by
  `PlaywrightExtension`, `set()` in `beforeEach`, `remove()` (never
  `set(null)`) in `afterEach`, and carries a Javadoc line stating that
  contract and why `remove()` specifically (pooled parallel workers). A
  unit test in `ui/src/test/java/dev/qacommons/ui/PlaywrightExtensionTest.java`
  (`@Tag` none — runs in default `mvn clean verify`) drives a real headless
  browser against a `data:text/html,...` URL and asserts a fresh `Page` is
  injected per test method under parallel execution, with
  `Assumptions.assumeTrue(...)` skipping gracefully (not failing) if
  Chromium isn't installed on the machine running the build — the
  assumption message includes the exact `mvn -pl ui
  exec:java@install-browsers` command, not a generic "browser missing"
  string.

- [ ] T3: `UiSoftAssertions` soft-assertion screenshot hook — files:
  `ui/src/main/java/dev/qacommons/ui/UiSoftAssertions.java` — done when: a
  unit test in
  `ui/src/test/java/dev/qacommons/ui/UiSoftAssertionsTest.java` (no `@Tag`
  — runs by default, same `data:` URL / graceful-skip approach as T2)
  drives a real page, triggers a soft-assertion failure via
  `UiSoftAssertions`, and asserts a screenshot file was written under
  `ui/target/playwright-screenshots/` carrying the assertion's own message
  in the log output; a second unit test asserts that invoking
  `UiSoftAssertions`'s callback with no `Page` bound for the current thread
  throws `IllegalStateException` with an actionable message (register
  `PlaywrightExtension`), not a silent no-op.

- [ ] T4: hard-failure screenshot + trace retention wiring — files:
  `ui/src/main/java/dev/qacommons/ui/PlaywrightExtension.java` (extend
  `afterEach`) — done when: a unit test forces a hard test failure (e.g. an
  unhandled exception mid-test against a `data:` URL) inside a nested
  `@Nested`/programmatic JUnit Platform Launcher invocation (or an
  equivalent isolated harness) and asserts both a trace zip and a
  screenshot land under `ui/target/playwright-traces/` and
  `ui/target/playwright-screenshots/` with paths present in the captured
  log output; a matching *passing* test asserts no trace/screenshot is
  written (retain-on-failure only, no waste on green runs).

- [ ] T5: `SwaggerUiPage` + `OperationRow` page objects — files:
  `ui/src/test/java/dev/qacommons/ui/pages/SwaggerUiPage.java`,
  `ui/src/test/java/dev/qacommons/ui/pages/OperationRow.java` — done when:
  both compile against `BasePage`, locators are role-first per plan.md
  (`getByRole(LINK/BUTTON/TAB, ...)`, no CSS/XPath), and no method on
  either class accepts a `Locator` or `Page` parameter from a caller
  (tests only ever pass business values - tag names, operation summary
  text).

- [ ] T6: live Swagger UI tests — files:
  `ui/src/test/java/dev/qacommons/ui/tests/SwaggerUiTest.java`,
  `ui/src/test/resources/junit-platform.properties` (copied from
  `template`) — done when: with the notification service running
  (`docker-compose up -d --build`, per `template/README.md`), `mvn -pl ui
  -am test -DrunLive=true` runs the 3 `@Tag("live")` tests described in
  plan.md and all pass; with the service stopped, the same command fails
  loudly (not silently green, not a hang).

- [ ] T7: headless/headed flag — files:
  `ui/src/main/java/dev/qacommons/ui/UiConfig.java` (if not already
  complete from T2), `ui/src/main/java/dev/qacommons/ui/PlaywrightExtension.java`
  — done when: `mvn -pl ui -am test -DrunLive=true` launches headless by
  default (no visible window in a manual local check), and
  `QA_UI_HEADED=true mvn -pl ui -am test -DrunLive=true` launches a visible
  browser window for the same suite.

- [ ] T8: README — setup, running live UI tests, reading a trace — files:
  `README.md` (root, module list + one line in Build section), new
  `ui/README.md` — done when: `ui/README.md` documents the
  `exec:java@install-browsers` command, `-DrunLive=true` usage (mirroring
  `template/README.md`'s phrasing), the exact `mvn exec:java -e
  -Dexec.mainClass=com.microsoft.playwright.CLI
  -Dexec.args="show-trace <path>"` command for opening a saved trace, and a
  "complete verification sequence" as two explicit numbered steps (install
  browsers, then verify) with a note that a `Skipped` count in the `ui`
  module's Surefire summary means step 1 wasn't done, not that step 2
  failed.

- [ ] T9: full reactor + safety re-verification — files: none (verification
  only) — done when: fresh `mvn clean verify` from repo root passes with
  the notification service **down** - `ui` module included, zero
  `@Tag("live")` tests run, zero requests reach any live target
  (`template`'s existing exclusion is undisturbed); `mvn -pl ui -am test
  -DrunLive=true` passes with the service **up**; every checkbox in this
  file is `[x]` or explicitly moved to a Deferred section with a reason.
