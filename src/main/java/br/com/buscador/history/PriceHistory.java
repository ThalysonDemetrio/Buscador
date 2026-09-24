package br.com.buscador.history;

import br.com.buscador.offer.Offer;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class PriceHistory {

    private final JdbcClient jdbc;

    public PriceHistory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void record(List<Offer> offers) {
        String now = Instant.now().toString();
        for (Offer offer : offers) {
            jdbc.sql("""
                    INSERT INTO price_observation
                        (source, external_id, observed_at, effective_cost)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (source, external_id, observed_at) DO NOTHING
                    """)
                    .params(offer.source().name(), offer.externalId(), now,
                            offer.effectiveCost().toPlainString())
                    .update();
        }
    }

    public Optional<PriceChange> changeFor(Offer offer) {
        return jdbc.sql("""
                SELECT effective_cost FROM price_observation
                WHERE source = ? AND external_id = ?
                ORDER BY observed_at ASC
                LIMIT 1
                """)
                .params(offer.source().name(), offer.externalId())
                .query(String.class)
                .optional()
                .map(BigDecimal::new)
                .filter(previous -> previous.compareTo(offer.effectiveCost()) != 0)
                .map(previous -> new PriceChange(previous, offer.effectiveCost()));
    }
}
