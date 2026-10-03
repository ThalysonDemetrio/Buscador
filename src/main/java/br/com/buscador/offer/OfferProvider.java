package br.com.buscador.offer;

import java.util.List;

public interface OfferProvider {
    Source source();

    List<Offer> search(String term);

    /** O que a fonte consulta, para que "nenhum resultado" não pareça "a loja não vende". */
    List<String> coverage();
}
