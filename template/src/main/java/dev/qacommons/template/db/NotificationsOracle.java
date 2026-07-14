package dev.qacommons.template.db;

import dev.qacommons.db.PostgresDatabase;
import dev.qacommons.db.config.DbConfig;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Thread-safe: delegates every call to a fresh {@link PostgresDatabase}
 * connection, no shared mutable state. The one place this framework names
 * the real {@code notifications} table - {@code qa-commons-db} itself stays
 * table-agnostic.
 */
public final class NotificationsOracle {

    private final PostgresDatabase database;

    public NotificationsOracle(DbConfig config) {
        this.database = new PostgresDatabase(config);
    }

    public Optional<NotificationRow> findById(String id) {
        return database.queryOne(
                "SELECT id, recipient, channel, locked_by, locked_at, attempts_count "
                        + "FROM notifications WHERE id = ?",
                row -> new NotificationRow(
                        row.getString("id"),
                        row.getString("recipient"),
                        row.getString("channel"),
                        row.getString("locked_by"),
                        row.getObject("locked_at", LocalDateTime.class),
                        row.getInt("attempts_count")),
                id);
    }
}
