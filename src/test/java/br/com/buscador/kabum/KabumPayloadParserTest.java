package br.com.buscador.kabum;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KabumPayloadParserTest {

    private final KabumPayloadParser parser = new KabumPayloadParser();
    private String html;

    @BeforeEach
    void loadFixture() throws IOException {
        try (var in = getClass().getResourceAsStream(
                "/fixtures/kabum-memoria-ram-2026-09-24.html")) {
            html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void extractsEveryProductFromDoubleEncodedPayload() {
        List<KabumProduct> products = parser.parse(html).products();
        assertThat(products).hasSize(3);
        assertThat(products).extracting(KabumProduct::code)
                .containsExactly("922165", "313833", "564716");
    }

    @Test
    void readsMoneyAsBigDecimalWithoutBinaryFloatError() {
        KabumProduct husky = parser.parse(html).products().getFirst();
        assertThat(husky.priceWithDiscount()).isEqualByComparingTo(new BigDecimal("699.99"));
        assertThat(husky.price()).isEqualByComparingTo(new BigDecimal("823.52"));
    }

    @Test
    void readsSellerAndWarranty() {
        List<KabumProduct> products = parser.parse(html).products();
        KabumProduct own = products.getFirst();
        assertThat(own.sellerName()).isEqualTo("KaBuM!");
        assertThat(own.marketplace()).isFalse();
        assertThat(own.warranty()).contains("3 anos de garantia");

        KabumProduct thirdParty = products.get(1);
        assertThat(thirdParty.sellerName()).isEqualTo("UP DISTRIBUIDORA");
        assertThat(thirdParty.marketplace()).isTrue();
        assertThat(thirdParty.warranty()).isEqualTo("Sem Garantia");
    }

    @Test
    void readsTotalPagesFromPagination() {
        assertThat(parser.parse(html).totalPages()).isEqualTo(19);
    }

    @Test
    void failsLoudlyWhenPayloadIsMissing() {
        assertThatThrownBy(() -> parser.parse("<html><body>sem script</body></html>"))
                .isInstanceOf(KabumPayloadException.class)
                .hasMessageContaining("__NEXT_DATA__");
    }
}
