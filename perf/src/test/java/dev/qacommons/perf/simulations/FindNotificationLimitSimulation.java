package dev.qacommons.perf.simulations;

import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;

import dev.qacommons.core.config.QaConfig;
import dev.qacommons.perf.protocol.NotificationServiceProtocol;
import dev.qacommons.perf.scenarios.NotificationScenarios;
import io.gatling.javaapi.core.Assertion;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;

/**
 * MANUAL-ONLY. Never scheduled, never automated, never part of any CI or
 * default build - run it by hand, on purpose, when you actually want to
 * find where the target breaks (e.g. {@code mvn -pl perf gatling:test
 * -Dgatling.simulationClass=dev.qacommons.perf.simulations.FindNotificationLimitSimulation}
 * after installing dependencies - see perf/README.md).
 *
 * <p>Ramps from 1 to 50 requests/second over 5 minutes and measures the
 * target's queue/intake capacity (the Postgres-backed queue, its worker
 * pool, and poll batching) - <b>not</b> its per-recipient rate limiter,
 * which every request here structurally avoids by construction (see
 * perf/README.md's "Rate limiting" section: each request uses a fresh,
 * effectively-unique recipient). Failures appearing as the ramp climbs are
 * therefore expected and are the point of running this - they mark where
 * intake capacity, not the rate limiter, gives out. If a {@code 429}
 * appears instead, that contradicts the rate-limiter design decision and is
 * a bug to investigate, not evidence of "finding the limit."
 *
 * <p>Carries one intentionally loose assertion - a completely dead target
 * should still show up as a failed Gatling run, not a deceptively "green"
 * report with zero real traffic - but it is not a pass/fail gate the way
 * {@link SmokeSimulation}'s assertions are.
 */
public class FindNotificationLimitSimulation extends Simulation {

    private static final HttpProtocolBuilder PROTOCOL = NotificationServiceProtocol.httpProtocol();
    private static final ScenarioBuilder SCENARIO =
            NotificationScenarios.sendNotificationJourney(QaConfig.fromEnv().datafakerSeed());

    private static final Assertion NOT_COMPLETELY_DEAD = global().successfulRequests().percent().gt(0.0);

    {
        setUp(SCENARIO.injectOpen(rampUsersPerSec(1).to(50).during(Duration.ofMinutes(5))))
                .assertions(NOT_COMPLETELY_DEAD)
                .protocols(PROTOCOL);
    }
}
