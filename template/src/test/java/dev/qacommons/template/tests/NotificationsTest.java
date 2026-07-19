package dev.qacommons.template.tests;

import static org.assertj.core.api.Assertions.assertThat;

import dev.qacommons.api.ApiResult;
import dev.qacommons.core.config.QaConfig;
import dev.qacommons.core.report.ReportContextExtension;
import dev.qacommons.template.api.FailedNotificationsEndpoint;
import dev.qacommons.template.api.NotificationsEndpoint;
import dev.qacommons.template.model.CreateNotificationRequest;
import dev.qacommons.template.model.ErrorResponse;
import dev.qacommons.template.model.FailedNotificationSummary;
import dev.qacommons.template.model.NotificationResponse;
import dev.qacommons.template.model.NotificationStatus;
import dev.qacommons.template.model.PageResponse;
import dev.qacommons.template.testdata.NotificationRequests;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Requires the real notification service running locally - see the root
 * README for how to start it. Run with {@code mvn -pl template test
 * -DrunLive=true}; excluded from the default {@code mvn clean verify}.
 */
@Tag("live")
@ExtendWith(ReportContextExtension.class)
class NotificationsTest {

    private QaConfig config() {
        return QaConfig.fromEnv();
    }

    private NotificationRequests requestsFor(TestInfo testInfo) {
        long seed = config().datafakerSeed() ^ testInfo.getTestMethod().orElseThrow().getName().hashCode();
        return new NotificationRequests(seed);
    }

    @Test
    void send_returns202Queued(TestInfo testInfo) {
        NotificationsEndpoint endpoint = new NotificationsEndpoint(config());
        CreateNotificationRequest request = requestsFor(testInfo).valid();

        ApiResult<NotificationResponse, ErrorResponse> result = endpoint.send(request);

        assertThat(result.status()).isEqualTo(202);
        assertThat(result.expectSuccess().status()).isEqualTo(NotificationStatus.QUEUED);
    }

    @Test
    void listFailedNotifications_returns200(TestInfo testInfo) {
        FailedNotificationsEndpoint endpoint = new FailedNotificationsEndpoint(config());

        ApiResult<PageResponse<FailedNotificationSummary>, ErrorResponse> result = endpoint.list(0, 20);

        assertThat(result.status()).isEqualTo(200);
        PageResponse<FailedNotificationSummary> body = result.expectSuccess();
        assertThat(body.items()).isNotNull();
        // Proves the generic TypeReference path against the real service: if
        // items deserialized as raw LinkedHashMaps instead of
        // FailedNotificationSummary, this call would throw ClassCastException
        // rather than return a value.
        body.items().forEach(item -> assertThat(item.notificationId()).isNotBlank());
    }

    @Test
    void send_missingRecipient_returns400ValidationFailed(TestInfo testInfo) {
        NotificationsEndpoint endpoint = new NotificationsEndpoint(config());
        CreateNotificationRequest request = requestsFor(testInfo).missingRecipient();

        ApiResult<NotificationResponse, ErrorResponse> result = endpoint.send(request);

        assertThat(result.status()).isEqualTo(400);
        assertThat(result.expectFailure().code()).isEqualTo("VALIDATION_FAILED");
        assertThat(result.expectFailure().details()).isNotEmpty();
    }

    @Test
    void send_duplicatePayload_producesTwoIndependentNotifications(TestInfo testInfo) {
        NotificationsEndpoint endpoint = new NotificationsEndpoint(config());
        CreateNotificationRequest request = requestsFor(testInfo).valid();

        ApiResult<NotificationResponse, ErrorResponse> first = endpoint.send(request);
        ApiResult<NotificationResponse, ErrorResponse> second = endpoint.send(request);

        assertThat(first.status()).isEqualTo(202);
        assertThat(second.status()).isEqualTo(202);
        assertThat(second.expectSuccess().notificationId()).isNotEqualTo(first.expectSuccess().notificationId());
    }
}
