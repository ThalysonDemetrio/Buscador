package br.com.buscador.kabum;

import br.com.buscador.catalog.CatalogCache;
import br.com.buscador.offer.Offer;
import br.com.buscador.offer.Source;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KabumProviderTest {

    private static final KabumProperties PROPERTIES = new KabumProperties(
            "https://x", "UA", Duration.ofHours(6), Duration.ZERO, 50,
            Map.of("/hardware/memoria-ram", BigDecimal.ZERO, "/hardware/fontes", BigDecimal.ZERO));

    @Test
    void reportsItsSource() {
        assertThat(new KabumProvider(new StubCatalogCache(List.of()), PROPERTIES).source())
                .isEqualTo(Source.KABUM);
    }

    @Test
    void returnsWhatTheCacheFinds() {
        Offer expected = new Offer(Source.KABUM, "1", "Memória RAM",
                new BigDecimal("10"), new BigDecimal("10"),
                true, "KaBuM!", "1 ano", false, "https://x");
        KabumProvider provider = new KabumProvider(new StubCatalogCache(List.of(expected)), PROPERTIES);

        assertThat(provider.search("memoria")).containsExactly(expected);
    }

    @Test
    void coversOnlyTheConfiguredCategories() {
        KabumProvider provider = new KabumProvider(new StubCatalogCache(List.of()), PROPERTIES);

        assertThat(provider.coverage())
                .containsExactlyInAnyOrder("/hardware/memoria-ram", "/hardware/fontes");
    }

    private static class StubCatalogCache extends CatalogCache {
        private final List<Offer> offers;

        StubCatalogCache(List<Offer> offers) {
            super(null);
            this.offers = offers;
        }

        @Override
        public List<Offer> search(String term) {
            return offers;
        }
    }
}
