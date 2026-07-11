package dev.qacommons.perf.simulations;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.global;

import dev.qacommons.core.config.QaConfig;
import dev.qacommons.perf.protocol.NotificationServiceProtocol;
import dev.qacommons.perf.scenarios.NotificationScenarios;
import io.gatling.javaapi.core.Assertion;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;

/**
 * "Is the perf module wired correctly end-to-end" - short, low-rate, with
 * pass/fail assertions. Run after any change to protocol/steps/scenario, or
 * whenever the target service changes.
 *
 * <p>Not rate-limit-sensitive (see perf/README.md): success-rate/p95 here
 * reflect intake + rate-limiter-check latency only, never 429s under normal
 * operation, since every request uses a fresh recipient.
 */
public class SmokeSimulation extends Simulation {

    private static final HttpProtocolBuilder PROTOCOL = NotificationServiceProtocol.httpProtocol();
    private static final ScenarioBuilder SCENARIO =
            NotificationScenarios.sendNotificationJourney(QaConfig.fromEnv().datafakerSeed());

    private static final Assertion SUCCESS_RATE = global().successfulRequests().percent().gte(99.0);
    private static final Assertion P95_RESPONSE_TIME = global().responseTime().percentile(95).lt(800);

    {
        setUp(SCENARIO.injectOpen(constantUsersPerSec(2).during(Duration.ofSeconds(30))))
                .assertions(SUCCESS_RATE, P95_RESPONSE_TIME)
                .protocols(PROTOCOL);
    }
}
