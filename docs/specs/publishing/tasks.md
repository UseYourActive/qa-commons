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

- [ ] T4: tag and push v0.1.0 — files: none (git only) — done when: `git tag
  v0.1.0` exists locally, pushed to `origin`, pointing at the commit that
  includes T1–T3.

- [ ] T5: verify the real JitPack build for v0.1.0 — files: none
  (verification only, findings folded back into plan.md's Risks if reality
  contradicts it) — done when: JitPack's build log for
  `UseYourActive/qa-commons` tag `v0.1.0` is fetched and confirmed green for
  all four modules; a sources jar for `qa-commons-core` and
  `qa-commons-api` is actually resolved via a real Maven fetch (not just
  seen listed in the build log); and the resolved `qa-commons-api` POM's
  dependency on `qa-commons-core` points at the `v0.1.0` tag coordinate, not
  `SNAPSHOT`. If `openjdk21` isn't honored, sources don't actually resolve,
  or the internal dependency resolves to SNAPSHOT, STOP, update plan.md's
  Risks with what was actually observed, apply the documented fallback (or
  report the finding if no fallback covers it), and re-verify before
  continuing.

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
