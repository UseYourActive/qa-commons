# qa-commons-ui

Playwright for Java lifecycle, Page Object base, and failure diagnostics.
`PlaywrightExtension` owns one `Browser` per worker thread (created lazily,
reused across every test that thread picks up - not one per JVM, since
Playwright's Java client isn't safe to share across threads) and a fresh
`BrowserContext` + `Page` per test.

## Setup: install browser binaries

One-time step, not automatic - `mvn clean verify` never triggers this on
its own (same "declared, not bound" idiom `perf`'s Gatling plugin uses):

```
mvn -pl ui exec:java@install-browsers
```

Installs Chromium only (this module's scope for now - Firefox/WebKit are
reachable via the same underlying CLI if ever needed, just not what
anything here launches). Playwright caches browser binaries in an
OS-specific directory outside this repo (on Windows, observed at
`%LOCALAPPDATA%\ms-playwright`); re-run this command if that cache ever
gets cleared.

## Running the tests

Plain `mvn clean verify` (or `mvn -pl ui -am test`) runs this module's
local-only tests - the `PlaywrightExtension`/`UiSoftAssertions` lifecycle
and diagnostics tests, all driven against `data:` URLs, no external service
needed. They **do** launch a real headless Chromium instance, so if browser
binaries aren't installed yet, they report **skipped** (not failed) with a
message naming the exact install command above - see "Complete
verification sequence" below for how to tell that apart from a real pass.

The live suite (`dev.qacommons.ui.tests`, tagged `@Tag("live")`) drives the
real notification service's Swagger UI and is excluded by default, same
gating as `template`:

```
mvn -pl ui -am test -DrunLive=true
```

(`-am` builds `core` first since it isn't installed to the local Maven
repo on a fresh clone.) See `template/README.md` for how to start the
notification service locally - the live suite targets the same
`QA_BASE_URL`-configured instance.

Headless by default; for headed debugging:

```
QA_UI_HEADED=true mvn -pl ui -am test -DrunLive=true
```

## Complete verification sequence

Two explicit steps, in order:

1. **Install browsers (once):** `mvn -pl ui exec:java@install-browsers`
2. **Verify:** `mvn clean verify`

If you only ever run step 2, this module's browser-dependent unit tests
report as `Skipped` in the Surefire summary rather than actually exercising
the Playwright lifecycle - a nonzero `Skipped` count for `qa-commons-ui`
means step 1 hasn't been done yet, not that something failed. Run both
steps to get a real, fully-exercised verification.

## Failure diagnostics

Every test gets Playwright tracing (retain-on-failure) and, on a hard
failure, a screenshot; every **soft**-assertion failure (via
`UiSoftAssertions`) gets its own screenshot at the moment it happens,
logged with the assertion's own message. Artifacts land under:

- `target/playwright-traces/<test-id>-<n>.zip`
- `target/playwright-screenshots/<test-id>-<n>.png`

with both paths printed in the failure log output - no digging through
`target/` blind.

Open a saved trace with Playwright's own trace viewer:

```
mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="show-trace target/playwright-traces/<file>.zip"
```

The trace viewer shows DOM snapshots, network activity, and console output
for the whole test - usually enough on its own, without needing the
screenshot at all.

## Writing new page objects

One class per page/screen, extending `BasePage`; locators stay private,
tests only ever see business-named methods (see `pages.SwaggerUiPage` /
`pages.OperationRow` for the pattern - composition for a sub-fragment like
`OperationRow`, not inheritance). Prefer role-based locators
(`page.getByRole(...)`) over CSS/XPath - `SwaggerUiPage` needed zero CSS
selectors. Use `Locator.waitFor()` for existence/visibility checks, never
`Locator.count()` or `Locator.isVisible()` on their own - both are
one-shot, non-retrying queries that can pass by accident under fast
(headless) rendering and fail under slower (headed) rendering; `waitFor()`
is Playwright's real auto-waiting primitive.
