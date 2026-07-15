package dev.qacommons.template.db;

import java.time.LocalDateTime;

/**
 * Only the columns {@code NotificationOracleTest} actually asserts on -
 * identity (id/recipient/channel) plus the claim columns the durable-queue
 * poller uses ({@code locked_by}/{@code locked_at}/{@code attempts_count}).
 * Extend when a real scenario needs {@code status}/{@code payload}/etc.,
 * not speculatively.
 */
public record NotificationRow(
        String id, String recipient, String channel,
        String lockedBy, LocalDateTime lockedAt, int attemptsCount) {
}
