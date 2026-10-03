package br.com.buscador.search;

import br.com.buscador.offer.Offer;
import br.com.buscador.offer.Source;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RelevanceOrderTest {

    private static Offer offer(String id, String title, String cost) {
        return new Offer(Source.KABUM, id, title, new BigDecimal(cost),
                new BigDecimal(cost), true, "KaBuM!", "1 ano", false, "https://x", null);
    }

    private static List<String> sortedIds(String term, Offer... offers) {
        return List.of(offers).stream()
                .sorted(RelevanceOrder.forTerm(term))
                .map(Offer::externalId)
                .toList();
    }

    /** Cenário real: suporte de R$ 24,99 aparecia antes das placas. */
    @Test
    void titleStartingWithTermComesBeforeCheaperAccessory() {
        assertThat(sortedIds("placa de video",
                offer("suporte", "Suporte Placa De Video Nvidia RTX", "24.99"),
                offer("gpu", "Placa De Vídeo Gt730 4gb", "310.55")))
                .containsExactly("gpu", "suporte");
    }

    @Test
    void prefixMatchIgnoresAccentsCaseAndExtraSpaces() {
        assertThat(sortedIds("  PLACA  DE VÍDEO ",
                offer("pasta", "Pasta Térmica para Placa de Vídeo", "71.24"),
                offer("gpu", "placa de video RX 7600", "1500.00")))
                .containsExactly("gpu", "pasta");
    }

    @Test
    void cheaperFirstWithinTheSameRelevance() {
        assertThat(sortedIds("placa de video",
                offer("rtx", "Placa de Vídeo RTX 4060", "1799.00"),
                offer("rx", "Placa de Vídeo RX 7600", "1500.00"),
                offer("sup-caro", "Suporte para Placa de Vídeo", "90.00"),
                offer("sup-barato", "Suporte para Placa de Vídeo", "25.00")))
                .containsExactly("rx", "rtx", "sup-barato", "sup-caro");
    }

    @Test
    void termInTheMiddleOfTitlesFallsBackToPrice() {
        assertThat(sortedIds("rtx 4060",
                offer("caro", "Placa de Vídeo RTX 4060 Ventus", "1899.00"),
                offer("barato", "Placa de Vídeo RTX 4060 Eagle", "1799.00")))
                .containsExactly("barato", "caro");
    }
}
