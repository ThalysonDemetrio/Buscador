package br.com.buscador.catalog;

import br.com.buscador.kabum.KabumClient;
import br.com.buscador.kabum.KabumNormalizer;
import br.com.buscador.kabum.KabumPage;
import br.com.buscador.kabum.KabumProperties;
import br.com.buscador.offer.Offer;
import org.junit.jupiter.api.Test;

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
                Duration.ofMinutes(30), List.of("catA", "catB", "catC"));

        new CatalogRefresher(client, NORMALIZER, cache, properties)
                .refreshStaleCategories();

        assertThat(cache.refreshedCategories).containsExactly("catA", "catC");
    }

    @Test
    void failingCategoryDoesNotPropagate() {
        StubKabumClient client = new StubKabumClient((category, page) -> {
            throw new RuntimeException("loja fora do ar");
        });
        RecordingCatalogCache cache = new RecordingCatalogCache();
        KabumProperties properties = new KabumProperties("https://x", "UA",
                Duration.ofMinutes(30), List.of("catA"));
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
                Duration.ofMinutes(30), List.of("catA"));

        new CatalogRefresher(client, NORMALIZER, cache, properties)
                .refreshStaleCategories();

        assertThat(cache.refreshedCategories).isEmpty();
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
        final Map<String, Instant> lastRefreshes = new HashMap<>();

        RecordingCatalogCache() {
            super(null);
        }

        @Override
        public void replaceCategory(String categoryPath, List<Offer> offers) {
            refreshedCategories.add(categoryPath);
        }

        @Override
        public Optional<Instant> lastRefresh(String categoryPath) {
            return Optional.ofNullable(lastRefreshes.get(categoryPath));
        }
    }
}
