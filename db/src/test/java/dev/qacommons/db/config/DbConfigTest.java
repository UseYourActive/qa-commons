package dev.qacommons.db.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DbConfigTest {

    @Test
    void fromEnv_usesDefaultsWhenNothingSet() {
        DbConfig config = DbConfig.fromEnv(key -> null);

        assertThat(config.host()).isEqualTo("localhost");
        assertThat(config.port()).isEqualTo(5432);
        assertThat(config.database()).isEqualTo("notificationdb");
        assertThat(config.user()).isEqualTo("postgres");
        assertThat(config.password()).isEqualTo("postgres");
    }

    @Test
    void fromEnv_readsOverridesFromLookup() {
        Map<String, String> env = Map.of(
                "QA_DB_HOST", "db.example.com",
                "QA_DB_PORT", "6543",
                "QA_DB_NAME", "otherdb",
                "QA_DB_USER", "someone",
                "QA_DB_PASSWORD", "secret");

        DbConfig config = DbConfig.fromEnv(env::get);

        assertThat(config.host()).isEqualTo("db.example.com");
        assertThat(config.port()).isEqualTo(6543);
        assertThat(config.database()).isEqualTo("otherdb");
        assertThat(config.user()).isEqualTo("someone");
        assertThat(config.password()).isEqualTo("secret");
    }

    @Test
    void fromEnv_blankHostFallsBackToDefault() {
        DbConfig config = DbConfig.fromEnv(key -> "QA_DB_HOST".equals(key) ? "   " : null);

        assertThat(config.host()).isEqualTo("localhost");
    }

    @Test
    void jdbcUrl_formatsHostPortDatabase() {
        DbConfig config = new DbConfig("db.example.com", 6543, "otherdb", "someone", "secret");

        assertThat(config.jdbcUrl()).isEqualTo("jdbc:postgresql://db.example.com:6543/otherdb");
    }

    @Test
    void fromEnv_noArgOverloadReadsRealEnvironment() {
        DbConfig config = DbConfig.fromEnv();

        assertThat(config.host()).isNotBlank();
        assertThat(config.port()).isPositive();
    }
}
