# qa-commons-perf

Gatling load tests against the notification service. **On-demand only** -
this module is wired into the parent reactor for compilation but is never
run by `mvn clean verify`. See the root `README.md` for that guarantee's
enforcement.

<!-- Prerequisites, exact run commands for both sims, and how to read a
     Gatling report are added in T8. -->

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
