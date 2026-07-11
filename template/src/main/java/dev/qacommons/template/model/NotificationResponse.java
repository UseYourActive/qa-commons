package dev.qacommons.template.model;

import java.time.LocalDateTime;

public record NotificationResponse(
        String notificationId,
        NotificationStatus status,
        String message,
        LocalDateTime timestamp,
        String recipient,
        String channel) {
}
