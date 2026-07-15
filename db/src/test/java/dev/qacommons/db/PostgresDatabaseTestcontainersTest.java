package dev.qacommons.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.qacommons.db.config.DbConfig;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Framework self-test: exercises {@link PostgresDatabase} against a real,
 * ephemeral Postgres 16 container (matching the target service's own image)
 * - no external service needed. Skips gracefully (not a failure) if Docker
 * isn't available locally, the same idiom {@code PlaywrightExtension} uses
 * for a missing Chromium install.
 */
class PostgresDatabaseTestcontainersTest {

    private static PostgreSQLContainer<?> postgres;
    private static PostgresDatabase database;

    @BeforeAll
    static void startContainer() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available locally - skipping PostgresDatabase self-tests.");

        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("oracle_selftest")
                .withUsername("oracle")
                .withPassword("oracle");
        postgres.start();

        DbConfig config = new DbConfig(postgres.getHost(), postgres.getMappedPort(5432),
                postgres.getDatabaseName(), postgres.getUsername(), postgres.getPassword());
        database = new PostgresDatabase(config);

        try (Connection connection = postgres.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE widgets (id VARCHAR(255) PRIMARY KEY, label VARCHAR(255) NOT NULL)");
            statement.execute("INSERT INTO widgets (id, label) VALUES ('w1', 'a widget with a '' quote')");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to seed self-test schema", e);
        }
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void queryOne_returnsMappedRowWhenFound() {
        Optional<String> label = database.queryOne(
                "SELECT label FROM widgets WHERE id = ?", row -> row.getString("label"), "w1");

        assertThat(label).contains("a widget with a ' quote");
    }

    @Test
    void queryOne_returnsEmptyWhenNotFound() {
        Optional<String> label = database.queryOne(
                "SELECT label FROM widgets WHERE id = ?", row -> row.getString("label"), "does-not-exist");

        assertThat(label).isEmpty();
    }

    @Test
    void queryOne_bindsParametersRatherThanConcatenating() {
        // A value containing a quote would break naive string concatenation
        // (SQL error, or worse a silent injection risk) - PreparedStatement
        // binding handles it transparently.
        Optional<String> label = database.queryOne(
                "SELECT label FROM widgets WHERE label = ?", row -> row.getString("label"),
                "a widget with a ' quote");

        assertThat(label).isPresent();
    }

    @Test
    void queryList_returnsEveryMatchedRow() {
        List<String> ids = database.queryList("SELECT id FROM widgets", row -> row.getString("id"));

        assertThat(ids).contains("w1");
    }

    @Test
    void queryOne_badSqlThrowsDatabaseOracleException_neverSwallowed() {
        assertThatThrownBy(() -> database.queryOne("SELECT * FROM no_such_table", row -> row.getString(1)))
                .isInstanceOf(DatabaseOracleException.class)
                .hasCauseInstanceOf(SQLException.class);
    }
}
