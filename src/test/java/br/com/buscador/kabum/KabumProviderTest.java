package br.com.buscador.kabum;

import br.com.buscador.catalog.CatalogCache;
import br.com.buscador.offer.Offer;
import br.com.buscador.offer.Source;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KabumProviderTest {

    @Test
    void reportsItsSource() {
        assertThat(new KabumProvider(new StubCatalogCache(List.of())).source())
                .isEqualTo(Source.KABUM);
    }

    @Test
    void returnsWhatTheCacheFinds() {
        Offer expected = new Offer(Source.KABUM, "1", "Memória RAM",
                new BigDecimal("10"), new BigDecimal("10"),
                true, "KaBuM!", "1 ano", false, "https://x");
        KabumProvider provider = new KabumProvider(new StubCatalogCache(List.of(expected)));

        assertThat(provider.search("memoria")).containsExactly(expected);
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
