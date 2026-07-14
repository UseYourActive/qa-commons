package dev.qacommons.db;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps the current row of a {@link ResultSet} to a typed value. Implementations
 * read columns only - never advance the cursor ({@code next()}) themselves.
 */
@FunctionalInterface
public interface RowMapper<T> {

    T map(ResultSet row) throws SQLException;
}
