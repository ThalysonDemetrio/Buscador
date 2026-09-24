package br.com.buscador.catalog;

import br.com.buscador.kabum.KabumClient;
import br.com.buscador.kabum.KabumNormalizer;
import br.com.buscador.kabum.KabumPage;
import br.com.buscador.kabum.KabumProperties;
import br.com.buscador.offer.Offer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class CatalogRefresher {

    private static final Logger log = LoggerFactory.getLogger(CatalogRefresher.class);

    private final KabumClient client;
    private final KabumNormalizer normalizer;
    private final CatalogCache cache;
    private final KabumProperties properties;

    public CatalogRefresher(KabumClient client, KabumNormalizer normalizer,
                            CatalogCache cache, KabumProperties properties) {
        this.client = client;
        this.normalizer = normalizer;
        this.cache = cache;
        this.properties = properties;
    }

    public void refreshStaleCategories() {
        for (String categoryPath : properties.categories()) {
            if (isStale(categoryPath)) {
                refresh(categoryPath);
            }
        }
    }

    private boolean isStale(String categoryPath) {
        return cache.lastRefresh(categoryPath)
                .map(at -> at.plus(properties.cacheTtl()).isBefore(Instant.now()))
                .orElse(true);
    }

    private void refresh(String categoryPath) {
        try {
            List<Offer> offers = new ArrayList<>();
            int discarded = 0;
            KabumPage first = client.fetchCategoryPage(categoryPath, 1);
            first.products().forEach(p -> offers.add(normalizer.toOffer(p)));
            discarded += first.discardedCount();
            for (int page = 2; page <= first.totalPages(); page++) {
                KabumPage next = client.fetchCategoryPage(categoryPath, page);
                next.products().forEach(p -> offers.add(normalizer.toOffer(p)));
                discarded += next.discardedCount();
            }
            cache.replaceCategory(categoryPath, offers);
            if (discarded > 0) {
                // Acumulado por categoria: o parser já logou item a item, mas o
                // total é o que diz se a cobertura daquela categoria ficou torta.
                log.warn("categoria {} atualizada: {} ofertas, {} produtos descartados",
                        categoryPath, offers.size(), discarded);
            } else {
                log.info("categoria {} atualizada: {} ofertas", categoryPath, offers.size());
            }
        } catch (RuntimeException e) {
            // Catch largo de propósito: falha de uma categoria não pode derrubar
            // as outras. Mas a exceção vai inteira para o log — sem o stacktrace,
            // um bug de programação viraria um WARN indistinguível de falha de
            // rede, e a categoria pararia de atualizar sem ninguém perceber.
            log.warn("falha ao atualizar {}", categoryPath, e);
        }
    }
}
