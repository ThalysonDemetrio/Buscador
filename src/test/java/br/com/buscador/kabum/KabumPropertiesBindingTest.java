package br.com.buscador.kabum;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Faz o binding do application.yml real, não de um KabumProperties montado à
 * mão: o Spring remove "/" de chave de Map sem colchetes, e o erro só aparece
 * com as regras de binding de verdade. Sem este teste, toda categoria vira uma
 * URL inválida e a falha fica escondida num WARN do refresher.
 */
class KabumPropertiesBindingTest {

    private static KabumProperties bindApplicationYml() throws IOException {
        var sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        return new Binder(ConfigurationPropertySources.from(sources))
                .bind("buscador.kabum", KabumProperties.class)
                .get();
    }

    @Test
    void categoryPathsKeepTheirSlashes() throws IOException {
        KabumProperties properties = bindApplicationYml();

        assertThat(properties.categories().keySet())
                .isNotEmpty()
                .allSatisfy(path -> assertThat(path).startsWith("/hardware/"))
                .contains("/hardware/placa-de-video-vga");
        assertThat(properties.categories().get("/hardware/placa-de-video-vga"))
                .isEqualByComparingTo("300.0");
    }

    /** Sem o valor no yml, o int viraria 0 e o refresher não baixaria página nenhuma, em silêncio. */
    @Test
    void pacingAndPageLimitAreConfigured() throws IOException {
        KabumProperties properties = bindApplicationYml();

        assertThat(properties.requestInterval()).isPositive();
        assertThat(properties.maxPages()).isPositive();
    }
}
