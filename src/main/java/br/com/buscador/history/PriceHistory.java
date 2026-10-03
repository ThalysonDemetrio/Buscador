package br.com.buscador.history;

import br.com.buscador.offer.Offer;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Guarda só as mudanças de preço: uma busca repetida com o mesmo preço não
 * gera linha nova, então a tabela cresce com o mercado, não com o uso.
 */
@Repository
public class PriceHistory {

    private enum Observation {
        FIRST("MIN"), LATEST("MAX");

        private final String aggregate;

        Observation(String aggregate) {
            this.aggregate = aggregate;
        }
    }

    private final JdbcClient jdbc;

    public PriceHistory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void record(List<Offer> offers) {
        Map<String, BigDecimal> latest = costs(offers, Observation.LATEST);
        String now = Instant.now().toString();
        for (Offer offer : offers) {
            BigDecimal previous = latest.get(key(offer));
            if (previous != null && previous.compareTo(offer.effectiveCost()) == 0) {
                continue;
            }
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

    /** Só entram no mapa as ofertas já vistas antes e cujo preço mudou desde a primeira vez. */
    public Map<Offer, PriceChange> changesFor(List<Offer> offers) {
        Map<String, BigDecimal> first = costs(offers, Observation.FIRST);
        Map<Offer, PriceChange> changes = new HashMap<>();
        for (Offer offer : offers) {
            BigDecimal previous = first.get(key(offer));
            if (previous != null && previous.compareTo(offer.effectiveCost()) != 0) {
                changes.put(offer, new PriceChange(previous, offer.effectiveCost()));
            }
        }
        return changes;
    }

    private Map<String, BigDecimal> costs(List<Offer> offers, Observation which) {
        if (offers.isEmpty()) {
            return Map.of();
        }
        List<Object> params = new ArrayList<>(offers.size() * 2);
        for (Offer offer : offers) {
            params.add(offer.source().name());
            params.add(offer.externalId());
        }
        String sql = """
                SELECT o.source, o.external_id, o.effective_cost
                FROM price_observation o
                WHERE (o.source, o.external_id) IN (VALUES %s)
                  AND o.observed_at = (
                      SELECT %s(i.observed_at) FROM price_observation i
                      WHERE i.source = o.source AND i.external_id = o.external_id)
                """.formatted(String.join(", ", Collections.nCopies(offers.size(), "(?, ?)")),
                which.aggregate);

        Map<String, BigDecimal> costs = new HashMap<>();
        jdbc.sql(sql).params(params).query(rs -> {
            costs.put(key(rs.getString("source"), rs.getString("external_id")),
                    new BigDecimal(rs.getString("effective_cost")));
        });
        return costs;
    }

    private static String key(Offer offer) {
        return key(offer.source().name(), offer.externalId());
    }

    private static String key(String source, String externalId) {
        return source + ":" + externalId;
    }
}
