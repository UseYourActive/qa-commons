package dev.qacommons.template.api;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.qacommons.api.ApiResult;
import dev.qacommons.api.Endpoint;
import dev.qacommons.core.config.QaConfig;
import dev.qacommons.template.model.ErrorResponse;
import dev.qacommons.template.model.FailedNotificationSummary;
import dev.qacommons.template.model.PageResponse;
import java.util.Map;

public final class FailedNotificationsEndpoint
        extends Endpoint<Void, PageResponse<FailedNotificationSummary>, ErrorResponse> {

    public FailedNotificationsEndpoint(QaConfig config) {
        super(config, "/api/v1/notifications/failed",
                new TypeReference<PageResponse<FailedNotificationSummary>>() {
                }, ErrorResponse.class);
    }

    public ApiResult<PageResponse<FailedNotificationSummary>, ErrorResponse> list(int page, int size) {
        return getWithQuery("", Map.of("page", page, "size", size));
    }
}
