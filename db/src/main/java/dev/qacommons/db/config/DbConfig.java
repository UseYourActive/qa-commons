package dev.qacommons.db.config;

import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe: immutable record, safe to share across threads once constructed.
 */
public record DbConfig(String host, int port, String database, String user, String password) {

    private static final Logger LOGGER = LoggerFactory.getLogger(DbConfig.class);

    private static final String HOST_ENV = "QA_DB_HOST";
    private static final String PORT_ENV = "QA_DB_PORT";
    private static final String DATABASE_ENV = "QA_DB_NAME";
    private static final String USER_ENV = "QA_DB_USER";
    private static final String PASSWORD_ENV = "QA_DB_PASSWORD";

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5432;
    private static final String DEFAULT_DATABASE = "notificationdb";
    private static final String DEFAULT_USER = "postgres";
    private static final String DEFAULT_PASSWORD = "postgres";

    public static DbConfig fromEnv() {
        return fromEnv(System::getenv);
    }

    public static DbConfig fromEnv(Function<String, String> lookup) {
        String host = blankToNull(lookup.apply(HOST_ENV));
        int port = parseIntOrDefault(lookup.apply(PORT_ENV), DEFAULT_PORT);
        String database = blankToNull(lookup.apply(DATABASE_ENV));
        String user = blankToNull(lookup.apply(USER_ENV));
        String password = blankToNull(lookup.apply(PASSWORD_ENV));

        DbConfig config = new DbConfig(
                host != null ? host : DEFAULT_HOST,
                port,
                database != null ? database : DEFAULT_DATABASE,
                user != null ? user : DEFAULT_USER,
                password != null ? password : DEFAULT_PASSWORD);

        LOGGER.info("DbConfig loaded: host={}, port={}, database={}, user={}, password=<redacted>",
                config.host(), config.port(), config.database(), config.user());
        return config;
    }

    /**
     * JDBC connection URL built from this config's host/port/database.
     */
    public String jdbcUrl() {
        return "jdbc:postgresql://%s:%d/%s".formatted(host(), port(), database());
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static int parseIntOrDefault(String value, int defaultValue) {
        String trimmed = blankToNull(value);
        return trimmed != null ? Integer.parseInt(trimmed.trim()) : defaultValue;
    }
}
