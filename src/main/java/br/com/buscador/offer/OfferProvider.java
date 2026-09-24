package br.com.buscador.offer;

import java.util.List;

public interface OfferProvider {
    Source source();

    List<Offer> search(String term);
}
