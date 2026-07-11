package dev.qacommons.template.api;

import dev.qacommons.api.ApiResult;
import dev.qacommons.api.Endpoint;
import dev.qacommons.core.config.QaConfig;
import dev.qacommons.template.model.ErrorResponse;
import dev.qacommons.template.model.FailedNotificationsPage;

public final class FailedNotificationsEndpoint extends Endpoint<Void, FailedNotificationsPage, ErrorResponse> {

    public FailedNotificationsEndpoint(QaConfig config) {
        super(config, "/api/v1/notifications/failed", FailedNotificationsPage.class, ErrorResponse.class);
    }

    public ApiResult<FailedNotificationsPage, ErrorResponse> list(int page, int size) {
        return get("?page=" + page + "&size=" + size);
    }
}
