# Generic response types for `Endpoint`

## Problem

`Endpoint<TReq, TRes, TErr>` only accepts `Class<TRes>` for the success body,
so `ResultClassifier` can only deserialize via `mapper.readValue(String,
Class<T>)`. Type erasure means that overload can never express a generic
envelope like `PageResponse<T>` — attempting `PageResponse.class` loses the
element type, and Jackson falls back to raw `LinkedHashMap`/`ArrayList`
elements. Consumers work around this today by hand-writing a concrete,
endpoint-specific shim record per paginated resource (this repo's own
`template` module does exactly that: `FailedNotificationsPage`, a one-off
wrapper around `List<FailedNotificationSummary>`, instead of a reusable
`PageResponse<FailedNotificationSummary>`). Every new paginated endpoint
repeats the shim instead of reusing a generic container.

## Goal / Non-goals

Goals:
- Add an additive `Endpoint` constructor overload accepting
  `TypeReference<TRes>` for the success type, alongside the existing
  `Class<TRes>` constructor — existing subclasses recompile unchanged.
- Thread the generic type through `ResultClassifier` so `Success` bodies
  deserialize with full generic fidelity (a `PageResponse<Foo>`'s `items`
  come back as `Foo`, not `LinkedHashMap`).
- Prove it: a stub-server unit test asserting element type on a round-tripped
  generic envelope.
- Prove it at the template level: convert `FailedNotificationsEndpoint` from
  the hand-written `FailedNotificationsPage` shim to a new reusable
  `PageResponse<T>` + the `TypeReference` constructor.
- Document the new path in the root README, before/after the existing
  `Class`-based example.

Non-goals:
- No generic path for `TErr` (error bodies) — out of scope; errors stay
  `Class`-based.
- No changes to `HttpEngine`, `LoggingFilter`, `RawResponse`, or any other
  `api`-module surface.
- No deletion of the notification repo's `FailedNotificationsPageResponse`
  shim — that's an external repo, out of scope, follow-up after v0.4.0 ships.
- No new dependency — Jackson (already on the classpath via `core`) already
  ships `TypeReference`/`JavaType`.

## Design

- **`TypeReference<TRes>` over `JavaType`, for the public constructor
  overload.** A `JavaType` must be built from a `TypeFactory`, and the only
  `TypeFactory` in play belongs to the `ObjectMapper` `Endpoint` constructs
  *inside* its own constructor — not yet available to a caller assembling
  constructor arguments. A caller could reach for
  `TypeFactory.defaultInstance()` instead, but that's an extra concept to
  teach for no benefit. `TypeReference<>(){}` is the standard Jackson idiom
  (mirrors `ObjectMapper.readValue(String, TypeReference)` directly) and
  needs nothing but the generic type itself. `JavaType` remains an internal
  detail: `Endpoint` converts whatever it's given (`Class` or
  `TypeReference`) into one `JavaType` field via
  `mapper.getTypeFactory().constructType(...)`, which is what actually flows
  into `ResultClassifier`.
- **`Endpoint<TReq, TRes, TErr>`** (`api/src/main/java/dev/qacommons/api/Endpoint.java`):
  add `protected Endpoint(QaConfig config, String basePath,
  TypeReference<TRes> successType, Class<TErr> errorType)` alongside the
  existing `Class<TRes>` constructor. Both constructors resolve to the same
  private `JavaType successType` field; the two `execute(...)` overloads are
  unchanged except for passing `JavaType` instead of `Class<TRes>` into
  `ResultClassifier.classify`.
- **`ResultClassifier`** (`api/src/main/java/dev/qacommons/api/internal/ResultClassifier.java`):
  change `classify`'s `Class<T> successType` parameter to `JavaType
  successType`, and use `mapper.readValue(String, JavaType)` for the
  `Success` path. This is an internal, non-public-API class (per `Endpoint`'s
  own Javadoc, consumers never touch it directly) so the signature change is
  invisible to consumers and not a compatibility concern. `errorType` stays
  `Class<E>` — out of scope per Non-goals.
- **New `PageResponse<T>`** record in `template` (not `api` — a generic
  envelope shape belongs to whichever service defines it, `api` stays
  shape-agnostic): `record PageResponse<T>(List<T> items, int page, int size,
  long totalItems, int totalPages)`, replacing `FailedNotificationsPage`.
  `FailedNotificationsEndpoint` becomes `Endpoint<Void,
  PageResponse<FailedNotificationSummary>, ErrorResponse>`, constructed via
  the new `TypeReference` overload.
- **Failure modes:** unchanged shape — a generic body that fails to
  deserialize (wrong element type, malformed JSON) still falls through to
  `ApiResult.Unparsed` exactly as the `Class`-based path does today; no new
  failure mode is introduced.

## Risks & open questions

- `TypeReference<TRes>() {}` is an anonymous subclass created per `Endpoint`
  construction (one per test / per resource instance, matching the class's
  existing not-thread-shared, one-per-test lifecycle) — negligible overhead,
  not worth optimizing away.
- Deleting the notification repo's `FailedNotificationsPageResponse` shim is
  explicitly deferred to a follow-up in that other repo, after this repo tags
  and JitPack resolves v0.4.0 — not tracked further here.
