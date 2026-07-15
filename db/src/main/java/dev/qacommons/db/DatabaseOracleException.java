package dev.qacommons.db;

/**
 * Thrown for any {@link java.sql.SQLException} a {@link PostgresDatabase} call
 * hits - bad SQL, an unreachable database, a type mismatch. Oracle failures
 * fail the test loudly; never caught, logged, and swallowed.
 */
public class DatabaseOracleException extends RuntimeException {

    public DatabaseOracleException(String message, Throwable cause) {
        super(message, cause);
    }
}
