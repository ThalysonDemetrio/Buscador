package br.com.buscador.kabum;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(KabumPayloadParser.class);
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
            if (dataNode.isMissingNode() || !dataNode.isString()) {
                throw new KabumPayloadException(
                        "props.pageProps.data ausente ou não é texto");
            }
            JsonNode inner = mapper.readTree(dataNode.asString());
            return new KabumPage(readProducts(inner), readTotalPages(inner));
        } catch (KabumPayloadException e) {
            throw e;
        } catch (Exception e) {
            throw new KabumPayloadException("payload ilegível", e);
        }
    }

    private List<KabumProduct> readProducts(JsonNode inner) {
        JsonNode array = inner.at("/catalogServer/data");
        if (array.isMissingNode()) {
            throw new KabumPayloadException(
                    "catalogServer.data ausente: o formato do payload mudou");
        }
        if (!array.isArray()) {
            throw new KabumPayloadException(
                    "catalogServer.data presente mas não é um array: o formato do payload mudou");
        }
        // Erro de UM produto descarta só aquele produto (dado faltando: degrada).
        // Erro de FORMATO (bloco try/catch de fora, em parse/readProducts/
        // readTotalPages antes deste laço) aborta tudo (dado errado: falha alto).
        // Um produto sem preço/código não é mentira, é ausência — perder 1 de 60
        // é muito melhor que perder os 60 por causa de um item "sob consulta".
        List<KabumProduct> products = new ArrayList<>(array.size());
        for (JsonNode node : array) {
            try {
                products.add(readProduct(node));
            } catch (KabumPayloadException e) {
                String code = node.path("code").asString("<sem code>");
                log.warn("Produto {} descartado: {}", code, e.getMessage());
            }
        }
        return products;
    }

    private KabumProduct readProduct(JsonNode node) {
        return new KabumProduct(
                requireText(node, "code"),
                requireText(node, "name"),
                // friendlyName só afeta a montagem da URL: "" degrada sem enganar.
                node.path("friendlyName").asString(""),
                requireMoney(node, "price"),
                requireMoney(node, "priceWithDiscount"),
                // ausência de "available" vira false: esconder um produto é mais
                // seguro do que oferecer algo que talvez não dê para comprar.
                node.path("available").asBoolean(false),
                textOrDefault(node, "sellerName", "Não informado"),
                // ausência do flag vira "é marketplace": tratar como vendedor
                // terceiro faz o aviso de garantia aparecer; o contrário
                // esconderia o risco do comprador.
                node.at("/flags/isMarketplace").asBoolean(true),
                // nunca null nem "": o KabumNormalizer (próxima task) depende de
                // warranty sempre ser um texto não vazio.
                textOrDefault(node, "warranty", "Não informado"));
    }

    private int readTotalPages(JsonNode inner) {
        JsonNode total = inner.at("/catalogServer/pagination/total");
        if (!total.isNumber()) {
            throw new KabumPayloadException(
                    "catalogServer.pagination.total ausente ou não numérico: "
                            + "o formato da paginação mudou");
        }
        return total.asInt();
    }

    /**
     * Campo obrigatório: identidade do produto ou texto exibido. Sem ele, nada
     * a fazer. String em branco conta como ausente — "code" vazio quebraria a
     * chave do histórico de preços (fonte + id) e colidiria produtos distintos.
     */
    private String requireText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asString().isBlank()) {
            throw new KabumPayloadException(
                    "campo obrigatório \"" + field + "\" ausente: o formato do produto mudou");
        }
        return value.asString();
    }

    /** Campo obrigatório: dinheiro. ZERO como fallback faria o produto parecer de graça. */
    private BigDecimal requireMoney(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isNumber()) {
            throw new KabumPayloadException(
                    "campo obrigatório \"" + field + "\" não é numérico: o formato do produto mudou");
        }
        return value.decimalValue();
    }

    /** Texto opcional: nunca deixa passar null nem "" adiante, só o default escolhido. */
    private String textOrDefault(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return defaultValue;
        }
        String text = value.asString();
        return text.isEmpty() ? defaultValue : text;
    }
}
