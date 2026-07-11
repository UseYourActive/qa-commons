package dev.qacommons.template.model;

import java.util.Map;

public record CreateNotificationRequest(
        NotificationChannel channel,
        String recipient,
        String templateName,
        String message,
        Map<String, Object> data) {
}
