package br.com.buscador.kabum;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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

    @Test
    void failsWhenPriceIsNotNumeric() {
        String product = product(Map.of("priceWithDiscount", "\"R$ 699,99\""));
        String html = buildHtml(catalogServer(product, 5));

        assertThatThrownBy(() -> parser.parse(html))
                .isInstanceOf(KabumPayloadException.class)
                .hasMessageContaining("priceWithDiscount");
    }

    @Test
    void failsWhenProductCodeIsMissing() {
        String product = product(without("code"));
        String html = buildHtml(catalogServer(product, 5));

        assertThatThrownBy(() -> parser.parse(html))
                .isInstanceOf(KabumPayloadException.class)
                .hasMessageContaining("code");
    }

    @Test
    void failsWhenPaginationTotalIsMissing() {
        String html = buildHtml(
                "{\"data\":[" + product(Map.of()) + "],\"pagination\":{}}");

        assertThatThrownBy(() -> parser.parse(html))
                .isInstanceOf(KabumPayloadException.class)
                .hasMessageContaining("total");
    }

    @Test
    void defaultsMissingWarrantyToNaoInformado() {
        String product = product(without("warranty"));
        String html = buildHtml(catalogServer(product, 5));

        KabumProduct parsed = parser.parse(html).products().getFirst();
        assertThat(parsed.warranty()).isEqualTo("Não informado");
    }

    @Test
    void defaultsMissingAvailabilityToUnavailable() {
        String product = product(without("available"));
        String html = buildHtml(catalogServer(product, 5));

        KabumProduct parsed = parser.parse(html).products().getFirst();
        assertThat(parsed.available()).isFalse();
    }

    @Test
    void defaultsMissingMarketplaceFlagToThirdParty() {
        String product = product(without("flags"));
        String html = buildHtml(catalogServer(product, 5));

        KabumProduct parsed = parser.parse(html).products().getFirst();
        assertThat(parsed.marketplace()).isTrue();
    }

    /**
     * {@code Map.of} não aceita valor {@code null}, então esse helper monta
     * o mapa de override "remova este campo" com um {@link java.util.HashMap}.
     */
    private Map<String, String> without(String field) {
        Map<String, String> overrides = new java.util.HashMap<>();
        overrides.put(field, null);
        return overrides;
    }

    /**
     * Monta um produto JSON válido, permitindo sobrescrever campos: um valor
     * de override igual a {@code null} remove o campo do JSON (simula
     * ausência); qualquer outro valor é usado como está, já em formato JSON.
     */
    private String product(Map<String, String> overrides) {
        var fields = new java.util.LinkedHashMap<String, String>();
        fields.put("code", "922165");
        fields.put("name", "\"Produto Teste\"");
        fields.put("friendlyName", "\"produto-teste\"");
        fields.put("price", "100.00");
        fields.put("priceWithDiscount", "90.00");
        fields.put("available", "true");
        fields.put("sellerName", "\"KaBuM!\"");
        fields.put("flags", "{\"isMarketplace\":false}");
        fields.put("warranty", "\"1 ano de garantia\"");
        overrides.forEach((field, value) -> {
            if (value == null) {
                fields.remove(field);
            } else {
                fields.put(field, value);
            }
        });
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (var entry : fields.entrySet()) {
            if (!first) {
                json.append(",");
            }
            first = false;
            json.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
        }
        return json.append("}").toString();
    }

    private String catalogServer(String productJson, int totalPages) {
        return "{\"data\":[" + productJson + "],\"pagination\":{\"total\":" + totalPages + "}}";
    }

    /**
     * Reproduz a codificação dupla real: o script __NEXT_DATA__ é um JSON
     * cujo campo props.pageProps.data é uma STRING contendo outro JSON.
     */
    private String buildHtml(String catalogServerJson) {
        String inner = "{\"catalogServer\":" + catalogServerJson + "}";
        String escapedInner = inner.replace("\\", "\\\\").replace("\"", "\\\"");
        String outer = "{\"props\":{\"pageProps\":{\"data\":\"" + escapedInner + "\"}}}";
        return "<html><body><script id=\"__NEXT_DATA__\">" + outer + "</script></body></html>";
    }
}
