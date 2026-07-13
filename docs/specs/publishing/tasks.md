# Tasks: publishing

- [x] T1: jitpack.yml — files: `jitpack.yml` (new) — done: contains exactly
  the `jdk: [openjdk21]` config from plan.md and nothing else.

- [x] T2: source + javadoc jar attachment — files: `pom.xml` (root) — done:
  `mvn clean verify` produces `*-sources.jar` and `*-javadoc.jar` in
  `core/target`, `api/target`, and `template/target`. `perf` has no
  `src/main/java` (everything lives under `src/test/java` by its own
  design) so the plugins correctly skip it with "No sources/Javadoc in
  project" — not a failure, not a gap to force. No Java source file in any
  module was modified.

- [x] T3: README — versioning + "Using qa-commons in your project" — files:
  `README.md` — done: both new sections exist per plan.md (versioning
  convention, incl. the "tags cut on main only" amendment; JitPack repo
  snippet + per-module coordinate table + one minimal `Endpoint` usage
  example verified against the real `Endpoint`/`ApiResult`/`QaConfig` API);
  existing Modules/Build sections otherwise unchanged.

- [x] T4: tag and push v0.1.0 — files: none (git only) — done: tagged and
  pushed at `c1515d4` (the `feature/publishing` merge commit) on `main`.

- [x] T5: verify the real JitPack build for v0.1.0 — done: JitPack's build
  API for `UseYourActive/qa-commons` tag `v0.1.0` reports `status: ok`,
  `commit: c1515d4...` (matches `main` HEAD exactly), all five artifacts
  built (`qa-commons`, `qa-commons-api`, `qa-commons-core`,
  `qa-commons-perf`, `qa-commons-template`). `mvn dependency:get` against a
  clean scratch local repo actually resolved
  `qa-commons-core-v0.1.0-sources.jar` and `qa-commons-api-v0.1.0-sources.jar`
  (not just an HTTP existence check) and both contain real `.java` files.
  `qa-commons-api`'s resolved POM depends on `qa-commons-core:v0.1.0`, not
  SNAPSHOT. `openjdk21` was honored with no fallback needed — no plan.md
  amendment required.

- [x] T6: consumption proof in the notification service repo — files: in
  `C:\Users\alexo\IdeaProjects\notification`, on branch
  `feature/qa-commons-consumer-proof` (commit `cada7a8`, **pushed** for PR
  review — https://github.com/UseYourActive/Notification-Microservice/pull/new/feature/qa-commons-consumer-proof).
  Done: `pom.xml` gets the JitPack repository plus one test-scope
  `qa-commons-api:v0.1.0` dependency; new package
  `bg.sit_varna.sit.si.qacommons` holds `ChannelsEndpoint` (real `Endpoint`
  subclass over the service's own `GetChannelsResponse`/`ErrorResponse`
  DTOs) and `ChannelsQaCommonsConsumptionTest` (one `@Test`, asserts on
  `ApiResult`). No existing testkit file touched. Ran against the real
  service (`docker-compose up -d --build`, health check confirmed `UP`).
  Confirmed the jar, and separately the sources jar (via
  `mvn dependency:sources`), landed in the real `~/.m2/repository` under
  `com/github/UseYourActive/qa-commons/...v0.1.0`, resolved from JitPack
  through this consumer repo's own `pom.xml` config — the same path an IDE
  uses to attach sources.

  **Gating amendment, folded into the same commit** (this is the repo's
  first `@Tag("live")` test, so the policy lands with it, not as a
  follow-up): added `groups`/`excludedGroups` properties, wired them into
  the existing `maven-surefire-plugin` config, and a `live` profile
  (`-DrunLive=true`) — an exact mirror of qa-commons's own `template/pom.xml`
  pattern. One-line README note in "Testing & Validation". Verified both
  directions for real: service stopped (`docker-compose down`), plain
  `mvn test` — 163 tests, 0 failures, the live test not run at all; service
  restarted and confirmed healthy, `mvn test -DrunLive=true` — 1 test run
  (only the live-tagged one, matching the `groups=live` semantics), 0
  failures.

- [x] T7: full reactor + safety re-verification — done: fresh
  `mvn clean verify` from the qa-commons repo root — `BUILD SUCCESS` across
  all 5 reactor entries; `qa-commons-template` reports `Tests run: 0`
  (live-tagged tests still excluded by default); no `gatling` goal appears
  anywhere in the build log (Gatling still never runs via `verify`); every
  checkbox in this file is now `[x]`.
