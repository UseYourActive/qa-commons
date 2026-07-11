# qa-commons-core

Config, serialization, seeded test data, and shared assertion conventions used
by every other qa-commons module.

## Soft-assertion convention

Use AssertJ's built-in `SoftAssertionsExtension` — never a hand-rolled
verification wrapper — whenever a test needs to check several independent
facts about one result without stopping at the first failure:

```java
@ExtendWith(SoftAssertionsExtension.class)
class NotificationsTest {

    @InjectSoftAssertions
    SoftAssertions softly;

    @Test
    void create_returnsExpectedNotification() {
        var result = endpoint.create(request).expectSuccess();

        softly.assertThat(result.recipient()).isEqualTo(request.recipient());
        softly.assertThat(result.channel()).isEqualTo(request.channel());
    }
}
```

See `SoftAssertionConventionExampleTest` in `core`'s test sources for a
runnable reference.

## Seeded test data

Extend `SeededFaker` for any module-specific test-data factory. Construct one
instance per test (it's not thread-safe to share), and pass the seed from
`QaConfig.datafakerSeed()` so the seed used is always the one logged at
startup — reproducible from the log line alone.

## Config

`QaConfig.fromEnv()` loads a typed, immutable config record from environment
variables (`QA_BASE_URL`, `QA_SEED`, `QA_REQUEST_TIMEOUT_MS`), logging the
resolved values at INFO. No caching, no singleton — call it explicitly
wherever config is needed.
