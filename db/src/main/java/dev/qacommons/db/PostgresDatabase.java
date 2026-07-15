package dev.qacommons.db;

import dev.qacommons.db.config.DbConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Generic Postgres test oracle: read-mostly, {@link PreparedStatement}-only,
 * fail-loud on any {@link SQLException} (wrapped as {@link DatabaseOracleException},
 * never caught-and-swallowed). Thread-safe - holds no mutable state beyond an
 * immutable {@link DbConfig}; every call opens and closes its own
 * {@link Connection} via try-with-resources rather than sharing one.
 *
 * <p>Table-agnostic by design - this module knows nothing about any specific
 * schema. Consumers build their own typed queries/row mappers on top, e.g.
 * a project's own {@code NotificationsOracle}.
 */
public final class PostgresDatabase {

    private final DbConfig config;

    public PostgresDatabase(DbConfig config) {
        this.config = config;
    }

    /**
     * Runs {@code sql} with {@code params} bound positionally, returning the
     * mapped first row, or empty if the query matched no rows.
     */
    public <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapper.map(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseOracleException("Query failed: " + sql, e);
        }
    }

    /**
     * Runs {@code sql} with {@code params} bound positionally, returning every
     * matched row mapped in result order.
     */
    public <T> List<T> queryList(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(mapper.map(resultSet));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new DatabaseOracleException("Query failed: " + sql, e);
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(config.jdbcUrl(), config.user(), config.password());
    }

    private static void bind(PreparedStatement statement, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }
}
