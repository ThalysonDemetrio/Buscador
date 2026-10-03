package br.com.buscador.kabum;

import br.com.buscador.catalog.CatalogCache;
import br.com.buscador.offer.Offer;
import br.com.buscador.offer.OfferProvider;
import br.com.buscador.offer.Source;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sem @Primary de propósito: na Fatia 2 os providers convivem numa lista, e
 * nenhum tem precedência sobre o outro.
 */
@Component
public class KabumProvider implements OfferProvider {

    private final CatalogCache cache;
    private final KabumProperties properties;

    public KabumProvider(CatalogCache cache, KabumProperties properties) {
        this.cache = cache;
        this.properties = properties;
    }

    @Override
    public Source source() {
        return Source.KABUM;
    }

    @Override
    public List<Offer> search(String term) {
        return cache.search(term);
    }

    @Override
    public List<String> coverage() {
        return List.copyOf(properties.categories().keySet());
    }
}
