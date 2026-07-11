# qa-commons-perf

Gatling load tests against the notification service. **On-demand only** -
this module is wired into the parent reactor for compilation but is never
run by `mvn clean verify`. See the root `README.md` for that guarantee's
enforcement.

## Prerequisites

1. The notification service running locally with **placeholder** provider
   credentials — same setup as `template/README.md`. Do not point a perf
   run at a target with real provider credentials (see "Provider
   credentials & delivery amplification" below).
2. `core`/`api`/`template`/`perf` installed to your local Maven repo once:
   ```
   mvn install -pl perf -am -DskipTests
   ```
   This is required because `gatling:test` is a directly-invoked Mojo, not
   a lifecycle-phase goal — unlike `mvn -pl template -am test`, combining
   `-am` with `gatling:test` itself doesn't work (it applies the goal to
   every reactor project, including the `pom`-packaging root, which always
   fails with "No simulations to run" before reaching `perf`). Installing
   first, then invoking `gatling:test` scoped to `perf` alone, sidesteps
   that entirely. Re-run this step whenever `core`/`api`/`template` change.

## Running the simulations

Smoke sim — the "is it wired right" check, ~30 seconds, safe to run
whenever protocol/steps/scenario or the target service changes:

```
mvn -pl perf gatling:test -Dgatling.simulationClass=dev.qacommons.perf.simulations.SmokeSimulation
```

Find-the-limit sim — **MANUAL-ONLY**, ~5 minutes, ramps up to 50 requests/
second and is expected to produce failures near the top of the ramp (that's
the point — see the class Javadoc):

```
mvn -pl perf gatling:test -Dgatling.simulationClass=dev.qacommons.perf.simulations.FindNotificationLimitSimulation
```

Never schedule either of these — both are opt-in, human-triggered runs.

## Reading the report

Each run prints a path like
`perf/target/gatling/<simulation>-<timestamp>/index.html` — open it in a
browser. The parts worth looking at:

- **The assertions banner** at the very bottom of the console output (also
  on the report's landing page) — pass/fail for exactly the checks defined
  in the simulation class. This is the first thing to check, before any
  chart.
- **Global stats** (top of the page) — total requests, OK/KO split, mean/
  percentile response times, mean throughput. For the smoke sim, KO should
  be 0 and p95 should track well under the 800ms assertion threshold.
- **Response Time Percentiles over Time** chart — whether latency stays
  flat or climbs as load increases; on the find-the-limit sim, this is
  where "the limit" actually shows up.
- **Requests per second** chart — confirms the actual injected rate matched
  what the simulation asked for (Gatling reports this per-second, live,
  during the run too).

## Rate limiting

The target service rate-limits notification creation, but **per
`(recipient, channel)` pair, not globally** - each recipient gets its own
counter in Redis. Defaults (`bg.sit_varna.sit.si.config.redis.RedisConfig`):

| Channel  | Max requests | Window |
|----------|-------------:|-------:|
| EMAIL    | 10           | 1 hour |
| SMS      | 5            | 1 hour |
| TELEGRAM | 20           | 1 hour |

Exceeding it returns `429 Too Many Requests` with a `Retry-After` header,
**synchronously from `POST /api/v1/notifications/send`** - not something
that fails later, asynchronously. The service's own `.env.example` ships
`REDIS_RATE_LIMIT_ENABLED=false` - rate limiting is **disabled by default**
on a fresh local setup. A shared/staging perf target may have it enabled.

**Neither simulation in this module needs 429 tolerance, and neither adds
any.** `NotificationFeeders.perUser` draws a fresh, effectively-unique
recipient from Datafaker for every single request; since the limiter's key
includes the recipient, and recipients are never reused or pooled across
requests, it's not plausible to accumulate 10+ hits against the same
`(recipient, channel)` key within a run, regardless of injection rate. This
is also why `FindNotificationLimitSimulation` genuinely measures the
target's queue/intake capacity rather than its rate limiter.

**If a `429` ever shows up in a report anyway**, that means something
violated the assumption above - e.g. a feeder regression that reused a
recipient, or a target configured with unusually small limits. Investigate
it as a bug. Do not "fix" it by adding 429 tolerance to an assertion; that
would silently mask exactly the kind of feeder/config regression this
design is supposed to make impossible.

## Provider credentials & delivery amplification

Perf runs **require placeholder provider credentials** in the target's
`.env` (`TELEGRAM_BOT_TOKEN`, Twilio, SendGrid, etc.) - the same placeholder
values `template/README.md` already documents as sufficient for the
functional suite. **Never point a perf run at a target configured with real
provider credentials.** The functional suite sends a handful of requests;
a perf run sends dozens to thousands, and with real credentials every one
of them is a real email/SMS/Telegram message sent to a real (fake) address
at load rates.

With placeholder credentials, every single notification's delivery attempt
fails against the real provider API. That failure doesn't just disappear -
the target's own resilience logic amplifies it into real backend work well
past the point Gatling stops injecting load:

1. **Immediate retries** - `NotificationProcessor`'s `@Retry` (Layer 1, ~2
   seconds apart) makes up to **4 total processing attempts** per
   notification before giving up.
2. **Cold-queue churn** - once Layer 1 is exhausted, the notification is
   marked `FAILED` and rescheduled on a Redis-backed cold queue **5 minutes
   later**, for up to **5 more cycles** (each cycle repeating step 1). A
   single injected request can keep the target doing real work for **20+
   minutes** after your virtual users have all finished.

This is accepted as realistic backpressure - it's genuinely what the target
does under sustained failure-to-deliver - but it means Gatling's own
client-side numbers (response time, requests/sec) only describe the
*intake* side. "Requests actually reaching workers" and the retry/cold-queue
volume behind a report's headline numbers aren't visible in Gatling's
report at all; keep that in mind when comparing a perf run's injected rate
to how hard the target actually worked.

## Real data accumulates - there is no cleanup

Every perf run leaves real `Notification` rows in the target service's
database, permanently. The service has no dedup (confirmed in
`qa-commons`'s own template investigation, see
`docs/specs/qa-commons/plan.md`), so a smoke run's 60 requests are 60 new
rows, and a full find-the-limit run is thousands. This module does not
clean any of it up, and isn't going to - that would need DB access this
framework deliberately doesn't have (see `docs/specs/perf-module/plan.md`'s
Non-goals). Treat this as an accepted cost of perf-testing against a real
backend rather than a mock: run perf sims against a target you're fine
accumulating test data in (a local Docker Compose instance you can
`docker-compose down -v` when you're done is the easy answer), never
against anything you'd call a shared or persistent environment.
