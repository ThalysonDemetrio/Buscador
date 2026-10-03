package br.com.buscador.catalog;

import br.com.buscador.kabum.KabumClient;
import br.com.buscador.kabum.KabumNormalizer;
import br.com.buscador.kabum.KabumPage;
import br.com.buscador.kabum.KabumProduct;
import br.com.buscador.kabum.KabumProperties;
import br.com.buscador.offer.Offer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Não cobre a agregação de {@code discardedCount} (soma incluindo a
 * primeira página, buscada fora do laço). O total não sai de
 * {@code refresh(categoryPath)} por nenhum caminho observável — só decide
 * o nível do log (WARN vs INFO) — e não há forma de testá-lo sem inspecionar
 * texto de log. A decisão de não testar foi consciente: o dano de um erro
 * ali é a precisão de uma mensagem de log, não o comportamento do sistema,
 * o que não justifica mudar a assinatura de produção só para viabilizar o
 * teste.
 */
class CatalogRefresherTest {

    private static final KabumNormalizer NORMALIZER = new KabumNormalizer();
    private static final KabumPage EMPTY_PAGE = new KabumPage(List.of(), 1, 0);

    @Test
    void failingCategoryDoesNotStopTheOthers() {
        StubKabumClient client = new StubKabumClient((category, page) -> {
            if (category.equals("catB")) {
                throw new RuntimeException("loja fora do ar");
            }
            return EMPTY_PAGE;
        });
        RecordingCatalogCache cache = new RecordingCatalogCache();
        KabumProperties properties = new KabumProperties("https://x", "UA",
                Duration.ofMinutes(30), Duration.ZERO, 50, Map.of("catA", BigDecimal.ZERO, "catB", BigDecimal.ZERO, "catC", BigDecimal.ZERO));

        new CatalogRefresher(client, NORMALIZER, cache, properties)
                .refreshStaleCategories();

        assertThat(cache.refreshedCategories).containsExactlyInAnyOrder("catA", "catC");
    }

    @Test
    void failingCategoryDoesNotPropagate() {
        StubKabumClient client = new StubKabumClient((category, page) -> {
            throw new RuntimeException("loja fora do ar");
        });
        RecordingCatalogCache cache = new RecordingCatalogCache();
        KabumProperties properties = new KabumProperties("https://x", "UA",
                Duration.ofMinutes(30), Duration.ZERO, 50, Map.of("catA", BigDecimal.ZERO));
        CatalogRefresher refresher = new CatalogRefresher(client, NORMALIZER, cache, properties);

        assertThatCode(refresher::refreshStaleCategories).doesNotThrowAnyException();
    }

    @Test
    void skipsCategoriesThatAreStillFresh() {
        StubKabumClient client = new StubKabumClient((category, page) -> {
            throw new AssertionError("categoria fresca não deveria bater na loja");
        });
        RecordingCatalogCache cache = new RecordingCatalogCache();
        cache.lastRefreshes.put("catA", Instant.now());
        KabumProperties properties = new KabumProperties("https://x", "UA",
                Duration.ofMinutes(30), Duration.ZERO, 50, Map.of("catA", BigDecimal.ZERO));

        new CatalogRefresher(client, NORMALIZER, cache, properties)
                .refreshStaleCategories();

        assertThat(cache.refreshedCategories).isEmpty();
    }

    @Test
    void keepsOnlyOffersAtOrAboveTheCategoryFloorAcrossAllPages() {
        StubKabumClient client = new StubKabumClient((category, page) -> page == 1
                ? new KabumPage(List.of(product("suporte", "16.99"), product("gt730", "310.55")), 2, 0)
                : new KabumPage(List.of(product("abaixo", "299.99"), product("no-piso", "300.00")), 2, 0));
        RecordingCatalogCache cache = new RecordingCatalogCache();
        KabumProperties properties = new KabumProperties("https://x", "UA",
                Duration.ofMinutes(30), Duration.ZERO, 50, Map.of("gpu", new BigDecimal("300")));

        new CatalogRefresher(client, NORMALIZER, cache, properties)
                .refreshStaleCategories();

        assertThat(cache.savedOffers.get("gpu")).extracting(Offer::externalId)
                .containsExactlyInAnyOrder("gt730", "no-piso");
    }

    @Test
    void stopsAtThePageLimitEvenWhenTheStoreReportsMore() {
        List<Integer> requestedPages = new ArrayList<>();
        StubKabumClient client = new StubKabumClient((category, page) -> {
            requestedPages.add(page);
            return new KabumPage(List.of(product("p" + page, "100.00")), 5, 0);
        });
        RecordingCatalogCache cache = new RecordingCatalogCache();
        KabumProperties properties = new KabumProperties("https://x", "UA",
                Duration.ofMinutes(30), Duration.ZERO, 2, Map.of("cat", BigDecimal.ZERO));

        new CatalogRefresher(client, NORMALIZER, cache, properties)
                .refreshStaleCategories();

        assertThat(requestedPages).containsExactly(1, 2);
        assertThat(cache.savedOffers.get("cat")).hasSize(2);
    }

    private static KabumProduct product(String code, String price) {
        BigDecimal value = new BigDecimal(price);
        return new KabumProduct(code, "Placa de Vídeo " + code, code, value, value,
                true, "KaBuM!", false, "1 ano", null);
    }

    private static class StubKabumClient extends KabumClient {
        private final BiFunction<String, Integer, KabumPage> pages;

        StubKabumClient(BiFunction<String, Integer, KabumPage> pages) {
            super(null, null, null, null);
            this.pages = pages;
        }

        @Override
        public KabumPage fetchCategoryPage(String categoryPath, int pageNumber) {
            return pages.apply(categoryPath, pageNumber);
        }
    }

    private static class RecordingCatalogCache extends CatalogCache {
        final List<String> refreshedCategories = new ArrayList<>();
        final Map<String, List<Offer>> savedOffers = new HashMap<>();
        final Map<String, Instant> lastRefreshes = new HashMap<>();

        RecordingCatalogCache() {
            super(null);
        }

        @Override
        public void replaceCategory(String categoryPath, List<Offer> offers) {
            refreshedCategories.add(categoryPath);
            savedOffers.put(categoryPath, offers);
        }

        @Override
        public Optional<Instant> lastRefresh(String categoryPath) {
            return Optional.ofNullable(lastRefreshes.get(categoryPath));
        }
    }
}
