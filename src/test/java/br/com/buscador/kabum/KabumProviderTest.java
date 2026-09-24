package br.com.buscador.kabum;

import br.com.buscador.offer.Offer;
import br.com.buscador.offer.Source;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class KabumProviderTest {

    @Test
    void reportsItsSource() {
        assertThat(new KabumProvider(term -> {}, term -> java.util.List.of())
                .source()).isEqualTo(Source.KABUM);
    }

    @Test
    void refreshesBeforeSearching() {
        var order = new StringBuilder();
        KabumProvider provider = new KabumProvider(
                term -> order.append("refresh;"),
                term -> {
                    order.append("search;");
                    return java.util.List.of();
                });

        provider.search("memoria");

        assertThat(order.toString()).isEqualTo("refresh;search;");
    }

    @Test
    void returnsWhatTheCacheFinds() {
        Offer expected = new Offer(Source.KABUM, "1", "Memória RAM",
                new java.math.BigDecimal("10"), new java.math.BigDecimal("10"),
                true, "KaBuM!", "1 ano", false, "https://x");
        KabumProvider provider = new KabumProvider(
                term -> {}, term -> java.util.List.of(expected));

        assertThat(provider.search("memoria")).containsExactly(expected);
    }
}
