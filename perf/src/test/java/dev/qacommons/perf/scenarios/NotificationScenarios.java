package dev.qacommons.perf.scenarios;

import static io.gatling.javaapi.core.CoreDsl.scenario;

import dev.qacommons.perf.steps.NotificationSteps;
import dev.qacommons.perf.testdata.NotificationFeeders;
import io.gatling.javaapi.core.ScenarioBuilder;
import java.time.Duration;

public final class NotificationScenarios {

    private NotificationScenarios() {
    }

    /**
     * Feed a fresh request, pause briefly (real clients don't fire in
     * lockstep - without this every virtual user's first request would land
     * in the same scheduler tick), then send it.
     */
    public static ScenarioBuilder sendNotificationJourney(long seed) {
        return scenario("Send Notification")
                .feed(NotificationFeeders.perUser(seed))
                .pause(Duration.ofMillis(100), Duration.ofMillis(400))
                .exec(NotificationSteps.sendNotification());
    }
}
