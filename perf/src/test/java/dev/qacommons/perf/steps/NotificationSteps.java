package dev.qacommons.perf.steps;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.jmesPath;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.qacommons.core.json.JsonMapperFactory;
import dev.qacommons.template.model.CreateNotificationRequest;
import io.gatling.javaapi.core.ChainBuilder;

/**
 * The one HTTP call this module knows how to make, built from the request
 * model {@code template} already owns - {@code perf} never redefines the
 * notification shape.
 */
public final class NotificationSteps {

    /** Session key the feeder must populate for {@link #sendNotification()} to find its request body. */
    public static final String NOTIFICATION_REQUEST_SESSION_KEY = "notificationRequest";

    private static final ObjectMapper MAPPER = JsonMapperFactory.newMapper();

    private NotificationSteps() {
    }

    public static ChainBuilder sendNotification() {
        return exec(http("Send Notification")
                .post("/api/v1/notifications/send")
                .body(StringBody(session -> writeValue(session.get(NOTIFICATION_REQUEST_SESSION_KEY))))
                .asJson()
                .check(status().is(202), jmesPath("notificationId").exists()));
    }

    private static String writeValue(CreateNotificationRequest request) {
        try {
            return MAPPER.writeValueAsString(request);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize request body: " + request, e);
        }
    }
}
