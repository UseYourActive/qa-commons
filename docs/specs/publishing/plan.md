# publishing

## Problem

qa-commons only exists as a local multi-module reactor. Nothing outside this
repo can depend on `core`/`api` without a manual `mvn install` into the
consumer's own `~/.m2`, which means no other project (starting with the
notification service's own test suite) can pull in the framework as a real
versioned dependency, get IDE-resolved sources/javadoc, or pin to a specific
release instead of a moving local checkout.

## Goal / Non-goals

Goals:
- The repo builds cleanly on JitPack (public, GitHub-hosted) as a multi-module
  Maven reactor targeting JDK 21, publishing `core`, `api`, `template`, and
  `perf` as independently-consumable artifacts.
- Each published module jar has an attached `-sources.jar` and `-javadoc.jar`
  so a consumer gets IDE navigation/docs, not just bytecode.
- `v0.1.0` is tagged and pushed as the first real release; the README states
  the versioning convention going forward (tags are releases, the pom's
  `-SNAPSHOT` version is for local development only and is never itself a
  consumable coordinate).
- A README section ("Using qa-commons in your project") gives a consumer
  everything needed to add the JitPack repository and the per-module
  dependency coordinates, with one minimal example.
- End-to-end proof that the whole loop works: from the notification service
  repo, on a branch, one new API test added that depends on
  `qa-commons-api` purely via its JitPack coordinate (no local reactor
  reference), with a real endpoint subclass and an `ApiResult` assertion
  against the running service, resolving cleanly in the IDE.

Non-goals:
- No change to any public class/method in `core` or `api` — this mission
  publishes the existing surface, it does not evolve it. (Constraint,
  restated as a non-goal so it's checked at every task.)
- No migration of the notification service's existing testkit to qa-commons
  — the consumption proof is one additive test, not a cutover.
- No CI/GitHub Actions wiring for the JitPack build itself — JitPack builds
  on-demand when a coordinate is first requested (and can be pinged directly,
  see Design); this mission doesn't add a repo-side workflow to pre-warm it.
- No custom-groupId workaround (e.g. a GitHub Actions job that republishes
  under a `dev.qacommons`-style coordinate). JitPack's
  `com.github.UseYourActive` groupId is accepted as-is, per explicit
  instruction.
- `perf` continuing to be excluded from `mvn clean verify`'s actual Gatling
  *execution* is unchanged — see Design for why publishing sources/javadoc
  for it is a harmless, separate concern from that guard.
- `template` module's behavior/tests are not touched beyond inheriting the
  new source/javadoc plugin bindings from the root pom (packaging-only
  addition, not a functional change).

## Design

### GroupId: no pom changes needed

Verified against JitPack's own multi-module docs and a live example
(`jitpack/maven-modular`) and a closed bug report
(`jitpack/jitpack.io#2872`, "groupId rename problems for Maven multi-module
projects with **multiple** groupIds"):

- For a multi-module Maven build, JitPack republishes each module under
  `com.github.<User>.<Repo>:<module-artifactId>:<tag>` — i.e. groupId becomes
  `com.github.UseYourActive.qa-commons`, artifactId stays exactly what's
  declared in each module's own pom (`qa-commons-core`, `qa-commons-api`,
  `qa-commons-template`, `qa-commons-perf`).
- This rewrite happens at JitPack's resolution/repository layer — it does
  **not** require changing the groupId actually declared inside any
  `pom.xml`. The one documented failure mode (#2872) is specifically when
  different modules in the same reactor declare *different* groupIds from
  the parent; every module here already shares one groupId (`dev.qacommons`)
  with the parent, which is confirmed to be the safe case. So: zero groupId
  edits, anywhere, in any pom. This also means the api/core Non-goal (no
  public-surface change) is trivially satisfied for this part of the work —
  there's nothing to touch.

### jitpack.yml — JDK 21

```yaml
jdk:
  - openjdk21
```

That's the entire file. Confirmed via `jitpack/jitpack.io#6479` ("Support
Java 21"): a reporter confirmed this exact `jdk: - openjdk21` config builds
successfully; the issue was closed without a maintainer-documented
alternative, i.e. no SDKMAN `before_install` workaround is needed (unlike the
JDK 17-era guidance that circulated before native support landed). No custom
`install:` step is added — JitPack's default Maven auto-detection already
runs `mvn install` on a pom.xml at the repo root, which is exactly what a
multi-module reactor needs, and our root `pom.xml` already pins
`maven-compiler-plugin` to `3.13.0` (supports `release 21` natively).

**Risk carried forward to execution, not assumed away**: this is a single
community report on a since-closed issue, not JitPack's own documentation
(their public `BUILDING.md` still only shows an `openjdk9` example). T5 in
tasks.md exists specifically to verify the real build log for our tag before
calling publishing done, and that verification is deliberately stronger than
"the build went green": it must confirm (a) all four module artifacts
published under the `v0.1.0` tag, (b) sources jars specifically attached
*and resolvable* — actually pull one via Maven, not just see it listed in
the build log, since IDE support was a stated goal and the plugin wiring
producing a jar locally doesn't guarantee JitPack served it — and (c) that
`api`'s internal dependency on `core` resolved to the `v0.1.0` tag
coordinate, not a stray `SNAPSHOT` (which would mean JitPack silently fell
back to whatever `dependencyManagement` says instead of rewriting the
internal-module version like it's supposed to). If `openjdk21` turns out not
to be honored, the fallback is the SDKMAN `before_install` pattern:
```yaml
before_install:
  - sdk install java 21-tem
  - sdk use java 21-tem
```

### Source + javadoc jars

Added to the **root pom's** `<build><plugins>` (not `pluginManagement` —
these need to actually execute, inherited by every child module, not just be
version-pinned):

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-source-plugin</artifactId>
  <version>3.4.0</version>
  <executions>
    <execution>
      <id>attach-sources</id>
      <goals><goal>jar-no-fork</goal></goals>
    </execution>
  </executions>
</plugin>
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-javadoc-plugin</artifactId>
  <version>3.12.0</version>
  <configuration>
    <source>${maven.compiler.release}</source>
    <doclint>none</doclint>
    <quiet>true</quiet>
  </configuration>
  <executions>
    <execution>
      <id>attach-javadocs</id>
      <goals><goal>jar</goal></goals>
    </execution>
  </executions>
</plugin>
```

- Both goals default-bind to the `package` phase, so they run as part of the
  same `mvn install` JitPack already invokes — no extra JitPack config.
- `<doclint>none</doclint>` / `<quiet>true</quiet>`: the codebase has real
  but sparse Javadoc (confirmed by inspection — a handful of class-level
  comments in `core`/`api`, per this project's own "don't write filler
  comments" convention). Default strict doclint can fail a build on
  incidental malformed HTML in existing comments; since this mission's job
  is to publish the *existing* surface, not audit/rewrite every doc comment,
  relaxing doclint up front avoids a spurious release-blocking failure over
  something unrelated to publishing itself.
- Versions (`maven-source-plugin` 3.4.0, `maven-javadoc-plugin` 3.12.0):
  latest stable (non-beta) releases on Maven Central as of 2026-07, matching
  how every other third-party version in this pom is pinned explicitly
  rather than left floating.
- Applies to all four modules uniformly, including `perf`. This is a
  packaging-only addition (extra jars produced at `package` phase) — it does
  not touch the separate mechanism (no `<executions>` on
  `gatling-maven-plugin`) that keeps `mvn clean verify` from ever running a
  Gatling load test. The two are unrelated lifecycle concerns; nothing here
  changes perf's exclusion from default *execution*.

### Versioning convention

The root and all module `pom.xml` files keep their version at
`0.1.0-SNAPSHOT` permanently for local development — this mission does
**not** bump it to `0.1.0` at tag time, and no future release will bump it
either. This is the standard JitPack-friendly pattern (JitPack publishes
whatever's built at a given tag under that tag's own name, ignoring the
pom's internal version string entirely): a consumer depends on the git tag
(`v0.1.0`), never on the SNAPSHOT string, so there's no coordination problem
between "what the pom says" and "what was released." Documented explicitly
in the README so this isn't rediscovered by surprise later:
- Tags (`vX.Y.Z`, pushed to `origin`) are releases — the only thing a
  consumer should ever depend on.
- The pom's `X.Y.Z-SNAPSHOT` is a local-only development marker; it is never
  itself a valid consumer coordinate and is not expected to numerically
  track the most recent tag.
- This "pom never moves" convention is specific to JitPack, which publishes
  by tag regardless of the pom's internal version. If this library ever
  moves to Maven Central or GitHub Packages, versioning goes back to real
  per-release version bumps in the pom — that's a different publishing
  mechanism with different rules, not an extension of this one.

### Consumption proof

- Notification service repo: `C:\Users\alexo\IdeaProjects\notification`.
  Branch `feature/qa-commons-consumer-proof`, cut from that repo's own
  default branch. That branch is **not pushed and not merged** — left local
  for the user's own review.
- On that branch: add the JitPack repository (`https://jitpack.io`) to its
  `pom.xml`/`build.gradle` (whichever it uses), add exactly one dependency —
  `com.github.UseYourActive.qa-commons:qa-commons-api:v0.1.0` — and write one
  new endpoint subclass (extending `qa-commons-api`'s `Endpoint<TReq, TRes,
  TErr>`) plus one test method asserting on `ApiResult` against the running
  service. No existing testkit code in that repo is touched or migrated.
- "IDE resolution" is proven by the dependency actually downloading through
  Maven/Gradle from JitPack and the IDE indexing the attached sources jar
  (not just compiling headless) — confirmed by opening the new class in an
  IDE and jumping to `Endpoint`'s definition, landing in decompiled-free
  real source, not a stub.

### README updates

- New "Using qa-commons in your project" section: JitPack repository
  snippet, a table of per-module coordinates
  (`com.github.UseYourActive.qa-commons:qa-commons-<module>:<tag>`), and one
  minimal example (add the `api` dependency, subclass `Endpoint`, call it).
  Framed around `core`/`api` as the modules meant to be depended on
  externally — `template`/`perf` get a coordinate row too (JitPack publishes
  them regardless) but stay described as example/on-demand modules, matching
  their existing framing in the current README.
- New "Versioning" section per above.

### Failure modes

- JitPack build fails on the pushed tag (e.g. `openjdk21` not actually
  honored) → caught by T5 before the mission is called done, not discovered
  later by a consumer; fallback SDKMAN config documented above.
- Consumption-proof dependency resolves the jar but not sources (JitPack
  javadoc/sources publishing has known project-specific flakiness in some
  reports) → treated as a real finding, not silently downgraded to "binary
  works, good enough" — the mission's explicit deliverable is IDE support.
- Doclint relaxation masks a genuinely broken doc comment → acceptable;
  publishing the existing surface as-is is the goal, not a docs audit.

## Risks & open questions

- **Native `jdk: - openjdk21` support is confirmed only by one closed
  community issue, not JitPack's own docs** — real risk, mitigated by
  verifying the actual build log in T5 rather than assuming success from the
  pushed tag alone.
- **Notification service repo path is unknown until the user supplies it**
  — T6 blocks on this; every other task is independently completable
  without it.
- **JitPack's javadoc publishing for multi-module repos has had scattered
  community reports of not always picking up per-module javadoc jars**
  (vs. sources jars, which are more consistently reported working) — T5's
  build-log check should confirm both, and if javadoc specifically doesn't
  attach, that becomes a finding to report back rather than something to
  quietly work around with an untested plugin-config change.
