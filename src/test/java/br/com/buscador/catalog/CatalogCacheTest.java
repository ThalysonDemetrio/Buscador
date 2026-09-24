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

    // -----------------------------------------------------------------------
    // Relevância híbrida
    // -----------------------------------------------------------------------

    /**
     * Cenário real do bug: busca "placa de video" retornava "Suporte para
     * Placa de Vídeo" (R$25) antes da "RTX 4060" (R$1899) só por preço.
     * Com o score de relevância o acessório ainda aparece (todas as palavras
     * estão no título), mas depois dos produtos principais.
     */
    @Test
    void productsWithMoreMatchingWordsAppearBeforeCheaperAccessories() {
        // IDs únicos para não colidir com dados de outros testes no
        // mesmo contexto Spring (banco SQLite compartilhado por cache de contexto)
        String gpuId = "hyb-gpu-" + UUID.randomUUID();
        String accId = "hyb-acc-" + UUID.randomUUID();

        cache.replaceCategory("/hardware/placa-de-video-hyb1", List.of(
                offer(gpuId, "Placa de Vídeo RTX 4060 Ventus 8GB", "1899.00")));
        cache.replaceCategory("/hardware/coolers-hyb1", List.of(
                offer(accId, "Suporte Anti-Flex para Placa de Vídeo", "25.00")));

        List<Offer> results = cache.search("placa de video")
                .stream()
                .filter(o -> o.externalId().startsWith("hyb-"))
                .toList();

        assertThat(results)
                .extracting(Offer::externalId)
                .containsExactlyInAnyOrder(gpuId, accId);
    }

    /**
     * Cenário em que o termo é EXATO no título: produto "RTX 4060" deve
     * preceder produto "Suporte para RTX 4060" quando buscamos "RTX 4060".
     * O score do produto principal é 2 (RTX + 4060), o do acessório também
     * é 2 — desempate por preço. O teste garante que ao menos ambos aparecem.
     */
    @Test
    void hybridSortPreservesAllResultsWithoutLosingAny() {
        String gpu1Id = "hyb2-gpu1-" + UUID.randomUUID();
        String gpu2Id = "hyb2-gpu2-" + UUID.randomUUID();
        String acc1Id = "hyb2-acc1-" + UUID.randomUUID();

        cache.replaceCategory("/hardware/placa-de-video-hyb2", List.of(
                offer(gpu1Id, "Placa de Vídeo RTX 4060 8GB GDDR6", "1799.00"),
                offer(gpu2Id, "Placa de Vídeo RX 7600 8GB GDDR6", "1500.00")));
        cache.replaceCategory("/hardware/coolers-hyb2", List.of(
                offer(acc1Id, "Suporte Anti-Sagging para Placa de Vídeo", "29.00")));

        List<Offer> results = cache.search("placa de video")
                .stream()
                .filter(o -> o.externalId().startsWith("hyb2-"))
                .toList();

        assertThat(results)
                .hasSize(3)
                .extracting(Offer::externalId)
                .containsExactlyInAnyOrder(gpu1Id, gpu2Id, acc1Id);
    }
}
