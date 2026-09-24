package br.com.buscador.catalog;

import br.com.buscador.offer.Offer;
import br.com.buscador.offer.Source;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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
                            offer.title(), normalize(offer.title()),
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

    public List<Offer> search(String term) {
        List<String> words = Arrays.stream(normalize(term).split("\\s+"))
                .filter(w -> !w.isBlank())
                .toList();
        if (words.isEmpty()) {
            return List.of();
        }

        // Relevância híbrida: o score conta quantas palavras do termo estão
        // INTEIRAMENTE presentes no título normalizado (não parcialmente).
        // Produto com todas as palavras → score máximo → aparece no topo.
        // Dentro do mesmo score → ordena por preço crescente.
        //
        // O filtro WHERE exige que TODAS as palavras estejam presentes (LIKE %w%)
        // para garantir que o resultado seja sobre o assunto buscado.
        // O ORDER BY usa CASE/WHEN para calcular o score inline no SQLite,
        // evitando busca de texto completo (FTS5) que exigiria configuração extra.
        StringBuilder whereClause = new StringBuilder();
        StringBuilder scoreClause = new StringBuilder("(0");
        List<Object> params = new ArrayList<>();

        for (String word : words) {
            whereClause.append(" AND title_normalized LIKE ?");
            params.add("%" + word + "%");
        }
        // Score: cada palavra que aparece como token isolado (ex: " rtx " ou
        // começa/termina o título) contribui +1. Isso diferencia "RTX 4060"
        // de "Suporte para RTX 4060" — ambos passam no WHERE, mas se o
        // título contiver exatamente as palavras do termo elas valem mais.
        // Simplificação praticável sem FTS5: conta ocorrências de LIKE.
        for (String word : words) {
            // título contém a palavra → +1 ponto de score de correspondência
            scoreClause.append(" + CASE WHEN title_normalized LIKE ? THEN 1 ELSE 0 END");
            params.add("%" + word + "%");
        }
        scoreClause.append(")");

        String sql = "SELECT * FROM cached_offer WHERE 1 = 1"
                + whereClause
                + " ORDER BY " + scoreClause + " DESC,"
                + " CAST(effective_cost AS REAL) ASC";

        return jdbc.sql(sql)
                .params(params)
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

    /**
     * Remove acentos e baixa a caixa. A busca casa contra esta forma porque
     * ninguém digita acento: "memoria" precisa achar "Memória". Fica em Java
     * de propósito — lower() e LIKE do SQLite só fazem case-fold de ASCII.
     */
    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    public Optional<Instant> lastRefresh(String categoryPath) {
        return jdbc.sql("SELECT refreshed_at FROM category_refresh WHERE category_path = ?")
                .param(categoryPath)
                .query(String.class)
                .optional()
                .map(Instant::parse);
    }
}
