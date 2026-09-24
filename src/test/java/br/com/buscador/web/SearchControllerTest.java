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

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest carrega só a camada web. É deliberado: com @SpringBootTest, o
 * KabumProvider real entraria no contexto e o teste acessaria a rede.
 */
@WebMvcTest(SearchController.class)
@Import(SearchControllerTest.Providers.class)
class SearchControllerTest {

    static class Providers {
        @Bean
        OfferProvider workingProvider() {
            return new OfferProvider() {
                public Source source() { return Source.KABUM; }
                public List<Offer> search(String term) {
                    return List.of(new Offer(Source.KABUM, "1",
                            "Memória RAM Husky 8GB", new BigDecimal("699.99"),
                            new BigDecimal("823.52"), true, "KaBuM!", "3 anos",
                            false, "https://x"));
                }
            };
        }

        @Bean
        OfferProvider failingProvider() {
            return new OfferProvider() {
                public Source source() { return Source.MERCADO_LIVRE; }
                public List<Offer> search(String term) {
                    throw new IllegalStateException("fonte fora do ar");
                }
            };
        }

        @Bean
        PriceHistory noHistory() {
            return new PriceHistory(null) {
                public void record(List<Offer> offers) { }
                public java.util.Optional<br.com.buscador.history.PriceChange>
                        changeFor(Offer offer) { return java.util.Optional.empty(); }
            };
        }

        @Bean
        br.com.buscador.kabum.KabumProperties kabumProperties() {
            return new br.com.buscador.kabum.KabumProperties(
                    "https://www.kabum.com.br", "buscador-teste/1.0",
                    java.time.Duration.ofHours(6),
                    List.of("/hardware/memoria-ram", "/hardware/placa-de-video-vga"));
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
                .andExpect(jsonPath("$.offers[0].discountPercentage").value(15));
    }

    @Test
    void reportsFailedSourcesInsteadOfHidingThem() throws Exception {
        mvc.perform(get("/api/search").param("term", "memoria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failedSources[0]").value("Mercado Livre"));
    }

    @Test
    void rejectsBlankTerm() throws Exception {
        mvc.perform(get("/api/search").param("term", "  "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alwaysReportsWhichCategoriesAreCovered() throws Exception {
        mvc.perform(get("/api/search").param("term", "memoria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coveredCategories[0]")
                        .value("/hardware/memoria-ram"))
                .andExpect(jsonPath("$.coveredCategories[1]")
                        .value("/hardware/placa-de-video-vga"));
    }
}
