package br.com.buscador.kabum;

import br.com.buscador.offer.Offer;
import br.com.buscador.offer.Source;
import org.springframework.stereotype.Component;

@Component
public class KabumNormalizer {

    private static final String PRODUCT_URL = "https://www.kabum.com.br/produto/%s/%s";

    /**
     * priceWithDiscount vem igual a price quando não há desconto, então serve
     * como custo efetivo sem condicional. oldPrice não é usado: veio zerado em
     * boa parte dos produtos observados.
     */
    public Offer toOffer(KabumProduct product) {
        return new Offer(
                Source.KABUM,
                product.code(),
                product.name(),
                product.priceWithDiscount(),
                product.price(),
                product.available(),
                product.sellerName(),
                product.warranty(),
                product.marketplace(),
                PRODUCT_URL.formatted(product.code(), product.friendlyName()));
    }
}
