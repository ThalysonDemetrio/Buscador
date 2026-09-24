package br.com.buscador.offer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * effectiveCost é o preço à vista já com desconto, sem frete: frete depende de
 * CEP e não é conhecido no momento da busca.
 */
public record Offer(
        Source source,
        String externalId,
        String title,
        BigDecimal effectiveCost,
        BigDecimal referencePrice,
        boolean available,
        String seller,
        String warranty,
        boolean thirdPartySeller,
        String url
) {
    public Offer {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(externalId, "externalId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(effectiveCost, "effectiveCost");
        Objects.requireNonNull(referencePrice, "referencePrice");
    }

    public boolean hasDiscount() {
        return effectiveCost.compareTo(referencePrice) < 0;
    }

    public int discountPercentage() {
        if (!hasDiscount() || referencePrice.signum() == 0) {
            return 0;
        }
        return referencePrice.subtract(effectiveCost)
                .multiply(BigDecimal.valueOf(100))
                .divide(referencePrice, 0, RoundingMode.HALF_UP)
                .intValue();
    }
}
