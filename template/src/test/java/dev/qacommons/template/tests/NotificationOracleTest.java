package dev.qacommons.template.tests;

import static org.assertj.core.api.Assertions.assertThat;

import dev.qacommons.api.ApiResult;
import dev.qacommons.core.config.QaConfig;
import dev.qacommons.core.report.ReportContextExtension;
import dev.qacommons.db.config.DbConfig;
import dev.qacommons.template.api.NotificationsEndpoint;
import dev.qacommons.template.db.NotificationRow;
import dev.qacommons.template.db.NotificationsOracle;
import dev.qacommons.template.model.CreateNotificationRequest;
import dev.qacommons.template.model.ErrorResponse;
import dev.qacommons.template.model.NotificationResponse;
import dev.qacommons.template.testdata.NotificationRequests;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Requires the real notification service (and its Postgres, reachable via
 * the {@code QA_DB_*} env vars - see root README) running locally.
 * Demonstrates the DB test-oracle proving what the API response alone
 * can't: that the intake actually persisted a row, not just that it
 * returned 202. Run with {@code mvn -pl template test -DrunLive=true}.
 */
@Tag("live")
@ExtendWith(ReportContextExtension.class)
class NotificationOracleTest {

    private QaConfig config() {
        return QaConfig.fromEnv();
    }

    private NotificationRequests requestsFor(TestInfo testInfo) {
        long seed = config().datafakerSeed() ^ testInfo.getTestMethod().orElseThrow().getName().hashCode();
        return new NotificationRequests(seed);
    }

    @Test
    void send_persistsRowWithMatchingIdentityAndReadableClaimColumns(TestInfo testInfo) {
        NotificationsEndpoint endpoint = new NotificationsEndpoint(config());
        NotificationsOracle oracle = new NotificationsOracle(DbConfig.fromEnv());
        CreateNotificationRequest request = requestsFor(testInfo).valid();

        ApiResult<NotificationResponse, ErrorResponse> result = endpoint.send(request);
        NotificationResponse sent = result.expectSuccess();

        Optional<NotificationRow> row = oracle.findById(sent.notificationId());

        // Identity/existence only - never status/lockedBy/lockedAt *values*,
        // since the poller can claim the row at any point after intake (the
        // Q1 lesson: never assert on what races the poller).
        assertThat(row).isPresent();
        assertThat(row.get().id()).isEqualTo(sent.notificationId());
        assertThat(row.get().recipient()).isEqualTo(request.recipient());
        assertThat(row.get().channel()).isEqualTo(request.channel().name());
        assertThat(row.get().attemptsCount()).isGreaterThanOrEqualTo(0);
    }
}
