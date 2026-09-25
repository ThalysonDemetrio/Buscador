package br.com.buscador.catalog;

import br.com.buscador.offer.Offer;
import br.com.buscador.offer.Source;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CatalogCacheTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        Path db = Path.of(System.getProperty("java.io.tmpdir"),
                "buscador-cache-" + UUID.randomUUID() + ".db");
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db);
    }

    @Autowired
    private CatalogCache cache;

    private Offer offer(String id, String title, String cost) {
        return new Offer(Source.KABUM, id, title, new BigDecimal(cost),
                new BigDecimal(cost), true, "KaBuM!", "1 ano", false,
                "https://www.kabum.com.br/produto/" + id + "/x");
    }

    @Test
    void findsByCaseInsensitiveKeyword() {
        cache.replaceCategory("/hardware/memoria-ram", List.of(
                offer("1", "Memória RAM Husky Impulse 8GB DDR4", "699.99"),
                offer("2", "Placa de Vídeo RTX 4060", "1899.00")));

        assertThat(cache.search("husky")).extracting(Offer::externalId)
                .containsExactly("1");
        assertThat(cache.search("HUSKY")).hasSize(1);
    }

    @Test
    void requiresEveryTermToMatch() {
        cache.replaceCategory("/hardware/memoria-ram", List.of(
                offer("1", "Memória RAM Husky Impulse 8GB DDR4", "699.99"),
                offer("2", "Memória RAM Kingston 16GB DDR5", "899.00")));

        assertThat(cache.search("memoria ddr4")).extracting(Offer::externalId)
                .containsExactly("1");
    }

    @Test
    void preservesCentsExactly() {
        cache.replaceCategory("/hardware/memoria-ram",
                List.of(offer("1", "Memória RAM Husky", "699.99")));

        assertThat(cache.search("husky").getFirst().effectiveCost())
                .isEqualByComparingTo("699.99");
    }

    @Test
    void replacingCategoryRemovesProductsThatDisappeared() {
        cache.replaceCategory("/hardware/memoria-ram",
                List.of(offer("1", "Memória RAM Husky", "699.99"),
                        offer("2", "Memória RAM Kingston", "899.00")));
        cache.replaceCategory("/hardware/memoria-ram",
                List.of(offer("1", "Memória RAM Husky", "650.00")));

        assertThat(cache.search("memoria")).hasSize(1);
    }

    @Test
    void recordsRefreshInstantPerCategory() {
        assertThat(cache.lastRefresh("/hardware/memoria-ram")).isEmpty();
        cache.replaceCategory("/hardware/memoria-ram",
                List.of(offer("1", "Memória RAM Husky", "699.99")));
        assertThat(cache.lastRefresh("/hardware/memoria-ram")).isPresent();
    }

    @Test
    void findsAccentedTitleWhenSearchTermHasNoAccent() {
        cache.replaceCategory("/hardware/memoria-ram",
                List.of(offer("1", "Memória RAM Husky Impulse 8GB DDR4", "699.99")));

        assertThat(cache.search("memoria")).hasSize(1);
        assertThat(cache.search("MEMORIA")).hasSize(1);
        assertThat(cache.search("memória")).hasSize(1);
    }

    @Test
    void findsMultiWordTitleIgnoringAccentsAndCase() {
        cache.replaceCategory("/hardware/placa-de-video",
                List.of(offer("2", "Placa de Vídeo RTX 4060 Ventus", "1899.00")));

        assertThat(cache.search("placa de video")).hasSize(1);
        assertThat(cache.search("PLACA DE VÍDEO")).hasSize(1);
    }

    @Test
    void keepsOriginalTitleForDisplay() {
        cache.replaceCategory("/hardware/memoria-ram",
                List.of(offer("1", "Memória RAM Husky Impulse 8GB DDR4", "699.99")));

        assertThat(cache.search("memoria").getFirst().title())
                .isEqualTo("Memória RAM Husky Impulse 8GB DDR4");
    }
}
