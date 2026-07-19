# Tasks: generic-response-types

- [ ] T1: Change `ResultClassifier.classify` to take `JavaType successType`
      instead of `Class<T> successType`, deserializing via
      `mapper.readValue(String, JavaType)` — files:
      `api/src/main/java/dev/qacommons/api/internal/ResultClassifier.java` —
      done when: existing `ResultClassifierTest`/`EndpointTest` callers are
      updated to pass a `JavaType` and `mvn -pl api test` is green.
- [ ] T2: Add the `TypeReference<TRes>` constructor overload to `Endpoint`,
      converting both constructors' input to one internal `JavaType`
      field via `mapper.getTypeFactory().constructType(...)`, and update the
      class Javadoc with a second (generic) usage example — files:
      `api/src/main/java/dev/qacommons/api/Endpoint.java` — done when: both
      constructors compile, existing `Class`-based subclasses
      (`EndpointTest.WidgetsEndpoint`, `template`'s `NotificationsEndpoint`,
      `FailedNotificationsEndpoint`) compile unchanged, `mvn -pl api test`
      green.
- [ ] T3: Unit test proving generic fidelity: stub-server test with a
      `{items:[...]}`-shaped body, constructed via the new `TypeReference`
      overload, asserting each item's concrete element type (not
      `LinkedHashMap`) — files: `api/src/test/java/dev/qacommons/api/EndpointTest.java`
      (or a new sibling test file if that keeps `EndpointTest` from growing
      unwieldy) — done when: test fails on the old `Class`-only path (verify
      by temporarily reverting T1/T2 locally) and passes after it, `mvn -pl
      api test` green.
- [ ] T4: Add `PageResponse<T>` record to `template`, delete
      `FailedNotificationsPage`, and switch `FailedNotificationsEndpoint` to
      `Endpoint<Void, PageResponse<FailedNotificationSummary>,
      ErrorResponse>` via the new `TypeReference` constructor — files:
      `template/src/main/java/dev/qacommons/template/model/PageResponse.java`
      (new), `template/src/main/java/dev/qacommons/template/model/FailedNotificationsPage.java`
      (deleted), `template/src/main/java/dev/qacommons/template/api/FailedNotificationsEndpoint.java`
      — done when: any code referencing `FailedNotificationsPage` (tests,
      other template classes) is updated to `PageResponse<FailedNotificationSummary>`,
      `mvn -pl template -am test` green (not just compile — cheap, and
      catches any template unit-level reference the compile pass misses).
- [ ] T5: README: add a "Typing generic responses" section to the root
      README, directly after the existing `Class`-based example, showing the
      same before (`FailedNotificationsPage` shim) / after
      (`PageResponse<FailedNotificationSummary>` + `TypeReference`
      constructor) — files: `README.md` — done when: the section renders
      correctly and code fences match the file's existing style.
- [ ] T6: Full reactor check + live acceptance check — files: none
      (verification task) — done when: (a) `mvn clean verify` passes green
      at the repo root, confirming no Class-based consumer or `template`'s
      live-tagged tests regressed; AND (b) with the notification service up,
      `mvn -pl ui,template -am test -DrunLive=true` passes, proving the
      converted `FailedNotificationsEndpoint` deserializes the real
      `/failed` payload into `PageResponse<FailedNotificationSummary>` with
      correctly-typed items — this, not (a), is the mission's true
      acceptance check, since `clean verify` never executes the live suite.
- [ ] T7: Post-merge release ritual (tracked so it isn't forgotten between
      merge and report) — files: none — done when: `v0.4.0` is tagged on
      the merge commit and pushed; the JitPack build is verified to the
      usual standard — all artifacts build, a real `dependency:get` of
      `qa-commons-api:v0.4.0` (with sources) resolves, and `template`'s own
      dependency on this repo resolves to the `v0.4.0` tag.
