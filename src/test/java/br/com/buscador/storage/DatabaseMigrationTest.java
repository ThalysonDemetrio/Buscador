package br.com.buscador.storage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bancos criados antes do Flyway (pelo antigo schema.sql) têm as tabelas da V1
 * mas nenhum histórico de migração. Precisam subir para a versão atual sem
 * perder cache nem histórico de preços.
 */
@SpringBootTest(properties = "buscador.catalog.refresh.enabled=false")
class DatabaseMigrationTest {

    @DynamicPropertySource
    static void legacyDatabase(DynamicPropertyRegistry registry) throws IOException, SQLException {
        Path db = Path.of(System.getProperty("java.io.tmpdir"),
                "buscador-legacy-" + UUID.randomUUID() + ".db");
        String url = "jdbc:sqlite:" + db;
        String v1 = new ClassPathResource("db/migration/V1__esquema_inicial.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement()) {
            for (String sql : v1.split(";")) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
            statement.execute("""
                    INSERT INTO cached_offer VALUES ('KABUM', '1', '/hardware/memoria-ram',
                        'Memória', 'memoria', '10.00', '12.00', 1, 'KaBuM!', '1 ano', 0, 'https://x')""");
            statement.execute("""
                    INSERT INTO price_observation VALUES ('KABUM', '1', '2026-09-24T10:00:00Z', '12.00')""");
            statement.execute("""
                    INSERT INTO category_refresh VALUES ('/hardware/memoria-ram', '2026-09-24T10:00:00Z')""");
        }
        registry.add("spring.datasource.url", () -> url);
    }

    @Autowired
    private JdbcClient jdbc;

    @Test
    void upgradesALegacyDatabaseWithoutLosingData() {
        assertThat(jdbc.sql("SELECT name FROM pragma_table_info('cached_offer')")
                .query(String.class).list()).contains("image_url");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM cached_offer").query(Long.class).single())
                .isEqualTo(1);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM price_observation").query(Long.class).single())
                .isEqualTo(1);
    }

    @Test
    void expiresTheCacheSoTheNextRefreshBringsThePhotos() {
        assertThat(jdbc.sql("SELECT COUNT(*) FROM category_refresh").query(Long.class).single())
                .isZero();
    }
}
