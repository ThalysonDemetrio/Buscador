package br.com.buscador.kabum;

import br.com.buscador.offer.Offer;
import br.com.buscador.offer.Source;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class KabumNormalizerTest {

    private final KabumNormalizer normalizer = new KabumNormalizer();

    private KabumProduct product(BigDecimal price, BigDecimal withDiscount,
                                 boolean available, String seller,
                                 boolean marketplace, String warranty) {
        return new KabumProduct("922165", "Memória RAM Husky 8GB",
                "memoria-ram-husky-8gb", price, withDiscount, available,
                seller, marketplace, warranty);
    }

    @Test
    void usesPriceWithDiscountAsEffectiveCost() {
        Offer offer = normalizer.toOffer(product(new BigDecimal("823.52"),
                new BigDecimal("699.99"), true, "KaBuM!", false, "3 anos"));
        assertThat(offer.effectiveCost()).isEqualByComparingTo("699.99");
        assertThat(offer.referencePrice()).isEqualByComparingTo("823.52");
        assertThat(offer.hasDiscount()).isTrue();
    }

    @Test
    void handlesEqualPricesAsNoDiscount() {
        Offer offer = normalizer.toOffer(product(new BigDecimal("619"),
                new BigDecimal("619"), true, "KaBuM!", false, "1 ano"));
        assertThat(offer.effectiveCost()).isEqualByComparingTo("619");
        assertThat(offer.hasDiscount()).isFalse();
    }

    @Test
    void marksMarketplaceSellerAsThirdParty() {
        Offer offer = normalizer.toOffer(product(new BigDecimal("619"),
                new BigDecimal("619"), true, "UP DISTRIBUIDORA", true,
                "Sem Garantia"));
        assertThat(offer.thirdPartySeller()).isTrue();
        assertThat(offer.seller()).isEqualTo("UP DISTRIBUIDORA");
        assertThat(offer.warranty()).isEqualTo("Sem Garantia");
    }

    @Test
    void trustsAvailableFlagEvenWhenQuantityIsZero() {
        Offer offer = normalizer.toOffer(product(new BigDecimal("619"),
                new BigDecimal("619"), true, "GIGANTEC", true, "Sem Garantia"));
        assertThat(offer.available()).isTrue();
    }

    @Test
    void buildsProductUrlFromCodeAndSlug() {
        Offer offer = normalizer.toOffer(product(new BigDecimal("10"),
                new BigDecimal("10"), true, "KaBuM!", false, "1 ano"));
        assertThat(offer.url())
                .isEqualTo("https://www.kabum.com.br/produto/922165/memoria-ram-husky-8gb");
    }

    @Test
    void setsSourceToKabum() {
        Offer offer = normalizer.toOffer(product(new BigDecimal("10"),
                new BigDecimal("10"), true, "KaBuM!", false, "1 ano"));
        assertThat(offer.source()).isEqualTo(Source.KABUM);
        assertThat(offer.externalId()).isEqualTo("922165");
    }

    @Test
    void keepsAvailabilityAndSellerTypeIndependent() {
        KabumProduct indisponivelDaLoja = new KabumProduct("922165",
                "Memória RAM Husky 8GB", "memoria-ram-husky-8gb",
                new BigDecimal("823.52"), new BigDecimal("699.99"),
                false, "KaBuM!", false, "3 anos");

        Offer offer = normalizer.toOffer(indisponivelDaLoja);

        assertThat(offer.available()).isFalse();
        assertThat(offer.thirdPartySeller()).isFalse();

        KabumProduct disponivelDeTerceiro = new KabumProduct("313833",
                "Memória Kingston 8GB", "memoria-kingston-8gb",
                new BigDecimal("619"), new BigDecimal("619"),
                true, "UP DISTRIBUIDORA", true, "Sem Garantia");

        Offer outra = normalizer.toOffer(disponivelDeTerceiro);

        assertThat(outra.available()).isTrue();
        assertThat(outra.thirdPartySeller()).isTrue();
    }
}
