package br.com.buscador.offer;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OfferTest {

    private Offer offer(BigDecimal effective, BigDecimal reference) {
        return new Offer(Source.KABUM, "922165", "Memória RAM Husky 8GB",
                effective, reference, true, "KaBuM!", "3 anos de garantia",
                false, "https://www.kabum.com.br/produto/922165/x");
    }

    @Test
    void reportsDiscountWhenEffectiveCostIsBelowReference() {
        Offer o = offer(new BigDecimal("699.99"), new BigDecimal("823.52"));
        assertThat(o.hasDiscount()).isTrue();
        assertThat(o.discountPercentage()).isEqualTo(15);
    }

    @Test
    void reportsNoDiscountWhenPricesAreEqual() {
        Offer o = offer(new BigDecimal("619"), new BigDecimal("619"));
        assertThat(o.hasDiscount()).isFalse();
        assertThat(o.discountPercentage()).isZero();
    }

    @Test
    void rejectsNullEffectiveCost() {
        assertThatThrownBy(() -> offer(null, new BigDecimal("10")))
                .isInstanceOf(NullPointerException.class);
    }
}
