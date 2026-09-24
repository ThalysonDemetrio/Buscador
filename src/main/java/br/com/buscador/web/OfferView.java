package br.com.buscador.web;

import br.com.buscador.history.PriceChange;
import br.com.buscador.offer.Offer;

public record OfferView(
        String source,
        String title,
        String effectiveCost,
        String referencePrice,
        int discountPercentage,
        boolean available,
        String seller,
        String warranty,
        boolean thirdPartySeller,
        String url,
        Integer changeSinceFirstSeen
) {
    public static OfferView of(Offer offer, PriceChange change) {
        return new OfferView(
                offer.source().label(),
                offer.title(),
                offer.effectiveCost().toPlainString(),
                offer.referencePrice().toPlainString(),
                offer.discountPercentage(),
                offer.available(),
                offer.seller(),
                offer.warranty(),
                offer.thirdPartySeller(),
                offer.url(),
                change == null ? null : change.percentage());
    }
}
