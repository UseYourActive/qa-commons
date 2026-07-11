package dev.qacommons.perf.testdata;

import dev.qacommons.perf.steps.NotificationSteps;
import dev.qacommons.template.testdata.NotificationRequests;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;

/**
 * One {@link NotificationRequests} instance per feeder, wrapped so every
 * pull builds a fresh {@code CreateNotificationRequest} - no virtual user
 * ever reads or mutates a record another VU also holds. Gatling's own
 * {@code feed()} step synchronizes iterator access across virtual users;
 * what this class guarantees on its side is that every pull is a fresh
 * build, never a shared/reused instance.
 */
public final class NotificationFeeders {

    private NotificationFeeders() {
    }

    public static Iterator<Map<String, Object>> perUser(long seed) {
        NotificationRequests requests = new NotificationRequests(seed);
        return Stream
                .generate(() -> Map.<String, Object>of(
                        NotificationSteps.NOTIFICATION_REQUEST_SESSION_KEY, requests.valid()))
                .iterator();
    }
}
