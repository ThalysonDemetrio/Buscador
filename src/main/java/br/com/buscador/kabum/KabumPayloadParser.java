package br.com.buscador.kabum;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * O payload da KaBuM tem codificação dupla: props.pageProps.data é uma string
 * que contém outro documento JSON.
 */
@Component
public class KabumPayloadParser {

    private static final String SCRIPT_ID = "__NEXT_DATA__";

    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    public KabumPage parse(String html) {
        Element script = Jsoup.parse(html).getElementById(SCRIPT_ID);
        if (script == null) {
            throw new KabumPayloadException(
                    "script " + SCRIPT_ID + " ausente: o formato da página mudou");
        }
        try {
            JsonNode outer = mapper.readTree(script.data());
            JsonNode dataNode = outer.at("/props/pageProps/data");
            if (dataNode.isMissingNode() || !dataNode.isTextual()) {
                throw new KabumPayloadException(
                        "props.pageProps.data ausente ou não é texto");
            }
            JsonNode inner = mapper.readTree(dataNode.asText());
            return new KabumPage(readProducts(inner), readTotalPages(inner));
        } catch (KabumPayloadException e) {
            throw e;
        } catch (Exception e) {
            throw new KabumPayloadException("payload ilegível", e);
        }
    }

    private List<KabumProduct> readProducts(JsonNode inner) {
        JsonNode array = inner.at("/catalogServer/data");
        if (!array.isArray()) {
            throw new KabumPayloadException("catalogServer.data não é um array");
        }
        List<KabumProduct> products = new ArrayList<>(array.size());
        for (JsonNode node : array) {
            products.add(new KabumProduct(
                    node.path("code").asText(),
                    node.path("name").asText(),
                    node.path("friendlyName").asText(),
                    money(node, "price"),
                    money(node, "priceWithDiscount"),
                    node.path("available").asBoolean(),
                    node.path("sellerName").asText(),
                    node.at("/flags/isMarketplace").asBoolean(),
                    node.path("warranty").asText()));
        }
        return products;
    }

    private int readTotalPages(JsonNode inner) {
        return inner.at("/catalogServer/pagination/total").asInt(1);
    }

    private BigDecimal money(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.decimalValue() : BigDecimal.ZERO;
    }
}
