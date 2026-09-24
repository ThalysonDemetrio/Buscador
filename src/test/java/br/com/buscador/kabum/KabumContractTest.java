package br.com.buscador.kabum;

import br.com.buscador.robots.RobotsGuard;
import br.com.buscador.robots.RobotsRules;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acessa a KaBuM real. Não roda no build padrão.
 * Rodar com: ./mvnw test -Dgroups=contract
 *
 * Quando este teste falhar, o formato da página mudou e o parser precisa
 * de manutenção. É o mecanismo de detecção do risco aceito na spec.
 */
@Tag("contract")
class KabumContractTest {

    @Test
    void liveCategoryPageStillMatchesTheExpectedShape() {
        KabumProperties properties = new KabumProperties(
                "https://www.kabum.com.br", "buscador-interno/1.0",
                Duration.ofHours(6), Map.of("/hardware/memoria-ram", BigDecimal.ZERO));
        KabumClient client = new KabumClient(RestClient.create(),
                new RobotsGuard(RobotsRules.kabum()),
                new KabumPayloadParser(), properties);

        KabumPage page = client.fetchCategoryPage("/hardware/memoria-ram", 1);

        assertThat(page.products()).isNotEmpty();
        assertThat(page.totalPages()).isPositive();
        KabumProduct first = page.products().getFirst();
        assertThat(first.code()).isNotBlank();
        assertThat(first.name()).isNotBlank();
        assertThat(first.priceWithDiscount()).isPositive();
    }
}
