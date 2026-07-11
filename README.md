# qa-commons

Personal multi-module test automation framework.

## Requirements

- Java 21
- Maven 3.9+

## Modules

- `core` — typed env-based config, Jackson mapper factory, seeded datafaker
  base factory, shared AssertJ soft-assertion conventions.
- `api` — typed Endpoint Object layer over RestAssured (`Endpoint<TReq, TRes,
  TErr>`, sealed `ApiResult`). RestAssured is an internal implementation
  detail of this module only.
- `template` — living example: tests against a notification service proving
  the framework end to end.

## Build

```
mvn clean verify
```

Runs the full reactor. The `template` module's service-dependent tests are
tagged `@Tag("live")` and excluded by default, so this passes without any
external service running.

See `template/README.md` for how to run the live suite against a local
notification service.

## Design docs

See `docs/specs/qa-commons/plan.md` and `docs/specs/qa-commons/tasks.md` for
the design and task breakdown behind this framework.
