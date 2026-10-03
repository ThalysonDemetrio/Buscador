package br.com.buscador.storage;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * O driver do SQLite cria o arquivo do banco, mas não a pasta: sem isto, a
 * primeira execução numa máquina nova falha ao abrir ~/.buscador/buscador.db.
 *
 * <p>BeanFactoryPostProcessor porque roda antes de qualquer bean, inclusive o
 * DataSource, e já enxerga a URL final, com as propriedades de teste aplicadas.
 */
@Configuration(proxyBeanMethods = false)
public class SqliteFileConfiguration {

    private static final String SQLITE_URL_PREFIX = "jdbc:sqlite:";

    @Bean
    static BeanFactoryPostProcessor sqliteFolderCreator(Environment environment) {
        return beanFactory -> {
            String url = environment.getRequiredProperty("spring.datasource.url");
            Path folder = Path.of(url.substring(SQLITE_URL_PREFIX.length()))
                    .toAbsolutePath().getParent();
            try {
                Files.createDirectories(folder);
            } catch (IOException e) {
                throw new UncheckedIOException("não foi possível criar a pasta do banco " + folder, e);
            }
        };
    }
}
