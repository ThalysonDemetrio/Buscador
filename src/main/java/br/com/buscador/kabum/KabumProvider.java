package br.com.buscador.kabum;

import br.com.buscador.catalog.CatalogCache;
import br.com.buscador.catalog.CatalogRefresher;
import br.com.buscador.offer.Offer;
import br.com.buscador.offer.OfferProvider;
import br.com.buscador.offer.Source;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Sem @Primary de propósito: na Fatia 2 os providers convivem numa lista, e
 * nenhum tem precedência sobre o outro.
 */
@Component
public class KabumProvider implements OfferProvider {

    private final Consumer<String> refresh;
    private final Function<String, List<Offer>> lookup;

    /**
     * @Autowired é obrigatório aqui: a classe tem dois construtores, e sem a
     * anotação o Spring não escolhe nenhum — procura o construtor sem
     * argumentos, não encontra, e a aplicação não sobe.
     */
    @Autowired
    public KabumProvider(CatalogRefresher refresher, CatalogCache cache) {
        this(term -> refresher.refreshStaleCategories(), cache::search);
    }

    KabumProvider(Consumer<String> refresh, Function<String, List<Offer>> lookup) {
        this.refresh = refresh;
        this.lookup = lookup;
    }

    @Override
    public Source source() {
        return Source.KABUM;
    }

    @Override
    public List<Offer> search(String term) {
        refresh.accept(term);
        return lookup.apply(term);
    }
}
