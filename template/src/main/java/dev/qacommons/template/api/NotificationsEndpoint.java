package dev.qacommons.template.api;

import dev.qacommons.api.ApiResult;
import dev.qacommons.api.Endpoint;
import dev.qacommons.core.config.QaConfig;
import dev.qacommons.template.model.CreateNotificationRequest;
import dev.qacommons.template.model.ErrorResponse;
import dev.qacommons.template.model.NotificationResponse;

public final class NotificationsEndpoint
        extends Endpoint<CreateNotificationRequest, NotificationResponse, ErrorResponse> {

    public NotificationsEndpoint(QaConfig config) {
        super(config, "/api/v1/notifications/send", NotificationResponse.class, ErrorResponse.class);
    }

    public ApiResult<NotificationResponse, ErrorResponse> send(CreateNotificationRequest request) {
        return post(request);
    }
}
