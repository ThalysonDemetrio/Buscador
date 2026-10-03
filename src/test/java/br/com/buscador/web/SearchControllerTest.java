package br.com.buscador.web;

import br.com.buscador.offer.Offer;
import br.com.buscador.offer.OfferProvider;
import br.com.buscador.offer.Source;
import br.com.buscador.history.PriceHistory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest carrega só a camada web. É deliberado: com @SpringBootTest, o
 * KabumProvider real entraria no contexto e o teste acessaria a rede.
 */
@WebMvcTest(SearchController.class)
@Import(SearchControllerTest.Providers.class)
@ExtendWith(OutputCaptureExtension.class)
class SearchControllerTest {

    static class Providers {
        @Bean
        OfferProvider workingProvider() {
            return new OfferProvider() {
                public Source source() { return Source.KABUM; }
                public List<String> coverage() {
                    return List.of("/hardware/memoria-ram", "/hardware/placa-de-video-vga");
                }
                public List<Offer> search(String term) {
                    return List.of(
                            new Offer(Source.KABUM, "2",
                                    "Dissipador para Memória RAM", new BigDecimal("19.90"),
                                    new BigDecimal("19.90"), true, "KaBuM!", "1 ano",
                                    false, "https://y", "https://images.kabum.com.br/y_m.jpg"),
                            new Offer(Source.KABUM, "1",
                                    "Memória RAM Husky 8GB", new BigDecimal("699.99"),
                                    new BigDecimal("823.52"), true, "KaBuM!", "3 anos",
                                    false, "https://x", "https://images.kabum.com.br/x_m.jpg"));
                }
            };
        }

        @Bean
        OfferProvider failingProvider() {
            return new OfferProvider() {
                public Source source() { return Source.MERCADO_LIVRE; }
                public List<String> coverage() { return List.of("Mercado Livre: toda a loja"); }
                public List<Offer> search(String term) {
                    throw new IllegalStateException("fonte fora do ar");
                }
            };
        }

        @Bean
        PriceHistory noHistory() {
            return new PriceHistory(null) {
                public void record(List<Offer> offers) { }
                public java.util.Map<Offer, br.com.buscador.history.PriceChange>
                        changesFor(List<Offer> offers) { return java.util.Map.of(); }
            };
        }
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void returnsOffersFromWorkingProvider() throws Exception {
        mvc.perform(get("/api/search").param("term", "memoria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers[0].title").value("Memória RAM Husky 8GB"))
                .andExpect(jsonPath("$.offers[0].effectiveCost").value("699.99"))
                .andExpect(jsonPath("$.offers[0].discountPercentage").value(15))
                .andExpect(jsonPath("$.offers[0].imageUrl").value("https://images.kabum.com.br/x_m.jpg"));
    }

    @Test
    void ordersTitleStartingWithTermBeforeCheaperAccessory() throws Exception {
        mvc.perform(get("/api/search").param("term", "memoria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers[0].title").value("Memória RAM Husky 8GB"))
                .andExpect(jsonPath("$.offers[1].title").value("Dissipador para Memória RAM"));
    }

    @Test
    void reportsFailedSourcesInsteadOfHidingThem() throws Exception {
        mvc.perform(get("/api/search").param("term", "memoria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failedSources[0]").value("Mercado Livre"));
    }

    @Test
    void logsTheFailingSourceWithItsStacktrace(CapturedOutput output) throws Exception {
        mvc.perform(get("/api/search").param("term", "memoria"))
                .andExpect(status().isOk());

        assertThat(output)
                .contains("fonte Mercado Livre falhou")
                .contains("java.lang.IllegalStateException: fonte fora do ar")
                .containsPattern("\\tat br\\.com\\.buscador\\.web\\.SearchControllerTest");
    }

    @Test
    void rejectsBlankTerm() throws Exception {
        mvc.perform(get("/api/search").param("term", "  "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsCoverageOfEverySourceEvenWhenOneFails() throws Exception {
        mvc.perform(get("/api/search").param("term", "memoria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coveredCategories", containsInAnyOrder(
                        "/hardware/memoria-ram", "/hardware/placa-de-video-vga",
                        "Mercado Livre: toda a loja")));
    }
}
