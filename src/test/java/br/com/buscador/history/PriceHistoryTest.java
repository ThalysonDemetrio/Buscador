package br.com.buscador.history;

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
class PriceHistoryTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        Path db = Path.of(System.getProperty("java.io.tmpdir"),
                "buscador-history-" + UUID.randomUUID() + ".db");
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db);
    }

    @Autowired
    private PriceHistory history;

    private Offer offer(String id, String cost) {
        return new Offer(Source.KABUM, id, "Memória RAM Husky",
                new BigDecimal(cost), new BigDecimal(cost), true, "KaBuM!",
                "1 ano", false, "https://x");
    }

    @Test
    void reportsNoChangeWhenOfferWasNeverSeen() {
        assertThat(history.changeFor(offer("novo-1", "699.99"))).isEmpty();
    }

    @Test
    void reportsDropAgainstPreviousObservation() {
        history.record(List.of(offer("drop-1", "823.52")));
        Offer cheaper = offer("drop-1", "699.99");

        PriceChange change = history.changeFor(cheaper).orElseThrow();

        assertThat(change.previousCost()).isEqualByComparingTo("823.52");
        assertThat(change.isDrop()).isTrue();
        assertThat(change.percentage()).isEqualTo(-15);
    }

    @Test
    void reportsRiseAgainstPreviousObservation() {
        history.record(List.of(offer("rise-1", "100.00")));

        PriceChange change = history.changeFor(offer("rise-1", "120.00")).orElseThrow();

        assertThat(change.isDrop()).isFalse();
        assertThat(change.percentage()).isEqualTo(20);
    }

    @Test
    void comparesAgainstOldestObservationOfThatOffer() {
        history.record(List.of(offer("multi-1", "100.00")));
        history.record(List.of(offer("multi-1", "90.00")));

        PriceChange change = history.changeFor(offer("multi-1", "80.00")).orElseThrow();

        assertThat(change.previousCost()).isEqualByComparingTo("100.00");
    }

    @Test
    void keepsObservationsSeparatePerOffer() {
        history.record(List.of(offer("sep-1", "100.00")));

        assertThat(history.changeFor(offer("sep-2", "100.00"))).isEmpty();
    }
}
