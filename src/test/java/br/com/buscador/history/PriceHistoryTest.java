package br.com.buscador.history;

import br.com.buscador.offer.Offer;
import br.com.buscador.offer.Source;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "buscador.catalog.refresh.enabled=false")
class PriceHistoryTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        Path db = Path.of(System.getProperty("java.io.tmpdir"),
                "buscador-history-" + UUID.randomUUID() + ".db");
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db);
    }

    @Autowired
    private PriceHistory history;

    @Autowired
    private JdbcClient jdbc;

    private Offer offer(String id, String cost) {
        return new Offer(Source.KABUM, id, "Memória RAM Husky",
                new BigDecimal(cost), new BigDecimal(cost), true, "KaBuM!",
                "1 ano", false, "https://x");
    }

    private long observationsOf(String id) {
        return jdbc.sql("SELECT COUNT(*) FROM price_observation WHERE external_id = ?")
                .param(id).query(Long.class).single();
    }

    @Test
    void reportsNoChangeWhenOfferWasNeverSeen() {
        assertThat(history.changesFor(List.of(offer("novo-1", "699.99")))).isEmpty();
    }

    @Test
    void reportsDropAgainstPreviousObservation() {
        history.record(List.of(offer("drop-1", "823.52")));
        Offer cheaper = offer("drop-1", "699.99");

        PriceChange change = history.changesFor(List.of(cheaper)).get(cheaper);

        assertThat(change.previousCost()).isEqualByComparingTo("823.52");
        assertThat(change.isDrop()).isTrue();
        assertThat(change.percentage()).isEqualTo(-15);
    }

    @Test
    void reportsRiseAgainstPreviousObservation() {
        history.record(List.of(offer("rise-1", "100.00")));
        Offer pricier = offer("rise-1", "120.00");

        PriceChange change = history.changesFor(List.of(pricier)).get(pricier);

        assertThat(change.isDrop()).isFalse();
        assertThat(change.percentage()).isEqualTo(20);
    }

    @Test
    void comparesAgainstOldestObservationOfThatOffer() {
        history.record(List.of(offer("multi-1", "100.00")));
        history.record(List.of(offer("multi-1", "90.00")));
        Offer current = offer("multi-1", "80.00");

        PriceChange change = history.changesFor(List.of(current)).get(current);

        assertThat(change.previousCost()).isEqualByComparingTo("100.00");
    }

    @Test
    void keepsObservationsSeparatePerOffer() {
        history.record(List.of(offer("sep-1", "100.00")));

        assertThat(history.changesFor(List.of(offer("sep-2", "50.00")))).isEmpty();
    }

    @Test
    void reportsOnlyOffersWhosePriceChangedInOneCall() {
        history.record(List.of(offer("batch-1", "100.00"), offer("batch-2", "200.00")));
        Offer changed = offer("batch-1", "80.00");
        Offer unchanged = offer("batch-2", "200.00");
        Offer unseen = offer("batch-3", "300.00");

        Map<Offer, PriceChange> changes = history.changesFor(List.of(changed, unchanged, unseen));

        assertThat(changes).containsOnlyKeys(changed);
    }

    @Test
    void doesNotRecordARepeatedPrice() {
        history.record(List.of(offer("same-1", "100.00")));
        history.record(List.of(offer("same-1", "100.00")));
        history.record(List.of(offer("same-1", "100.0")));

        assertThat(observationsOf("same-1")).isEqualTo(1);
    }

    @Test
    void recordsAPriceThatReturnsAfterAChange() {
        history.record(List.of(offer("back-1", "100.00")));
        history.record(List.of(offer("back-1", "90.00")));
        history.record(List.of(offer("back-1", "100.00")));

        assertThat(observationsOf("back-1")).isEqualTo(3);
    }
}
