# Tasks: publishing

- [ ] T1: jitpack.yml — files: `jitpack.yml` (new) — done when: file contains
  exactly the `jdk: [openjdk21]` config from plan.md and nothing else.

- [ ] T2: source + javadoc jar attachment — files: `pom.xml` (root) — done
  when: `mvn clean verify` from repo root succeeds and produces
  `*-sources.jar` and `*-javadoc.jar` in `core/target`, `api/target`, and
  `template/target`. `perf` has no `src/main/java` (everything lives under
  `src/test/java` by its own design) so the plugins correctly skip it with
  "No sources/Javadoc in project" — not a failure, not a gap to force. No
  Java source file in any module is modified.

- [ ] T3: README — versioning + "Using qa-commons in your project" — files:
  `README.md` — done when: both new sections exist per plan.md (versioning
  convention; JitPack repo snippet + per-module coordinate table + one
  minimal `Endpoint` usage example), and the existing Modules/Build sections
  are otherwise unchanged.

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

- [ ] T6: consumption proof in the notification service repo — files: in
  `C:\Users\alexo\IdeaProjects\notification`, on branch
  `feature/qa-commons-consumer-proof` (cut from that repo's default branch;
  **not pushed, not merged** — left local for review). Done when: the
  JitPack repository + exactly one
  `com.github.UseYourActive.qa-commons:qa-commons-api:v0.1.0` dependency are
  added, one new endpoint subclass + one new test method (asserting on
  `ApiResult`) are added, the test passes against the running service, no
  existing testkit file in that repo is modified, and the branch is left
  committed-but-unpushed in that repo.

- [ ] T7: full reactor + safety re-verification — files: none (verification
  only) — done when: `mvn clean verify` passes cleanly from the qa-commons
  repo root after T1–T3 (fresh run, not reusing T2's build), `template`'s
  live-tagged tests are still excluded by default, `perf` still never runs
  Gatling, and every checkbox above is `[x]` or explicitly moved to a
  Deferred section with a reason.
