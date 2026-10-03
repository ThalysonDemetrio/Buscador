package br.com.buscador.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteFileConfigurationTest {

    @TempDir
    Path tmp;

    @Test
    void createsTheDatabaseFolderBeforeTheDataSourceOpensIt() {
        Path folder = tmp.resolve("dados").resolve("buscador");

        new ApplicationContextRunner()
                .withUserConfiguration(SqliteFileConfiguration.class)
                .withPropertyValues("spring.datasource.url=jdbc:sqlite:" + folder.resolve("buscador.db"))
                .run(context -> assertThat(folder).isDirectory());
    }
}
