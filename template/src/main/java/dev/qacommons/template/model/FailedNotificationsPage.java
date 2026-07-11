package dev.qacommons.template.model;

import java.util.List;

public record FailedNotificationsPage(
        List<FailedNotificationSummary> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {
}
