package dev.qacommons.template.model;

import java.time.LocalDateTime;

public record FailedNotificationSummary(
        String notificationId,
        String recipient,
        String channel,
        String templateName,
        NotificationStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
