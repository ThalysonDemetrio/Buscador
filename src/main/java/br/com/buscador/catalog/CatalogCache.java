package br.com.buscador.catalog;

import br.com.buscador.offer.Offer;
import br.com.buscador.offer.Source;
import br.com.buscador.search.TextNormalizer;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Cache local de ofertas baixadas por categoria, com busca por palavra-chave.
 *
 * <p>Substitui o endpoint de busca da KaBuM, proibido pelo {@code robots.txt}:
 * páginas de categoria (permitidas) são baixadas, os produtos ficam aqui, e a
 * busca acontece localmente sobre esses dados.
 */
@Repository
public class CatalogCache {

    private final JdbcClient jdbc;

    public CatalogCache(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void replaceCategory(String categoryPath, List<Offer> offers) {
        jdbc.sql("DELETE FROM cached_offer WHERE category_path = ?")
                .param(categoryPath).update();
        for (Offer offer : offers) {
            jdbc.sql("""
                    INSERT INTO cached_offer (source, external_id, category_path,
                        title, title_normalized, effective_cost, reference_price,
                        available, seller, warranty, third_party, url)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (source, external_id) DO UPDATE SET
                        category_path = excluded.category_path,
                        title = excluded.title,
                        title_normalized = excluded.title_normalized,
                        effective_cost = excluded.effective_cost,
                        reference_price = excluded.reference_price,
                        available = excluded.available,
                        seller = excluded.seller,
                        warranty = excluded.warranty,
                        third_party = excluded.third_party,
                        url = excluded.url
                    """)
                    .params(offer.source().name(), offer.externalId(), categoryPath,
                            offer.title(), TextNormalizer.normalize(offer.title()),
                            offer.effectiveCost().toPlainString(),
                            offer.referencePrice().toPlainString(),
                            offer.available() ? 1 : 0, offer.seller(),
                            offer.warranty(), offer.thirdPartySeller() ? 1 : 0,
                            offer.url())
                    .update();
        }
        jdbc.sql("""
                INSERT INTO category_refresh (category_path, refreshed_at)
                VALUES (?, ?)
                ON CONFLICT (category_path) DO UPDATE SET
                    refreshed_at = excluded.refreshed_at
                """)
                .params(categoryPath, Instant.now().toString())
                .update();
    }

    /**
     * Filtra por todas as palavras do termo, sem ordenar: a ordem de exibição
     * é decidida por quem junta as fontes ({@link br.com.buscador.search.RelevanceOrder}).
     */
    public List<Offer> search(String term) {
        List<String> words = TextNormalizer.words(term);
        if (words.isEmpty()) {
            return List.of();
        }

        String sql = "SELECT * FROM cached_offer WHERE "
                + String.join(" AND ", Collections.nCopies(words.size(),
                        "title_normalized LIKE ? ESCAPE '\\'"));

        return jdbc.sql(sql)
                .params(words.stream().map(w -> "%" + escapeLikeWildcards(w) + "%").toList())
                .query((rs, rowNum) -> new Offer(
                        Source.valueOf(rs.getString("source")),
                        rs.getString("external_id"),
                        rs.getString("title"),
                        new BigDecimal(rs.getString("effective_cost")),
                        new BigDecimal(rs.getString("reference_price")),
                        rs.getInt("available") == 1,
                        rs.getString("seller"),
                        rs.getString("warranty"),
                        rs.getInt("third_party") == 1,
                        rs.getString("url")))
                .list();
    }

    /** "%" e "_" digitados pelo usuário são texto, não curinga do LIKE. */
    private static String escapeLikeWildcards(String word) {
        return word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public Optional<Instant> lastRefresh(String categoryPath) {
        return jdbc.sql("SELECT refreshed_at FROM category_refresh WHERE category_path = ?")
                .param(categoryPath)
                .query(String.class)
                .optional()
                .map(Instant::parse);
    }
}
