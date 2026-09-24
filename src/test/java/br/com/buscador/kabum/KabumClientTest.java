package br.com.buscador.kabum;

import br.com.buscador.robots.DisallowedUrlException;
import br.com.buscador.robots.RobotsGuard;
import br.com.buscador.robots.RobotsRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KabumClientTest {

    private MockRestServiceServer server;
    private KabumClient client;
    private String fixture;

    @BeforeEach
    void setUp() throws IOException {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        KabumProperties properties = new KabumProperties(
                "https://www.kabum.com.br", "buscador-interno/1.0",
                Duration.ofHours(6), java.util.List.of("/hardware/memoria-ram"));
        client = new KabumClient(builder.build(),
                new RobotsGuard(RobotsRules.kabum()),
                new KabumPayloadParser(), properties);
        try (var in = getClass().getResourceAsStream(
                "/fixtures/kabum-memoria-ram-2026-09-24.html")) {
            fixture = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void fetchesFirstPageWithoutQueryParameters() {
        server.expect(requestTo("https://www.kabum.com.br/hardware/memoria-ram"))
                .andRespond(withSuccess(fixture, MediaType.TEXT_HTML));

        KabumPage page = client.fetchCategoryPage("/hardware/memoria-ram", 1);

        assertThat(page.products()).hasSize(3);
        assertThat(page.totalPages()).isEqualTo(19);
        server.verify();
    }

    @Test
    void addsPageNumberOnlyFromSecondPageOn() {
        server.expect(requestTo(
                        "https://www.kabum.com.br/hardware/memoria-ram?page_number=2"))
                .andRespond(withSuccess(fixture, MediaType.TEXT_HTML));

        client.fetchCategoryPage("/hardware/memoria-ram", 2);

        server.verify();
    }

    @Test
    void refusesDisallowedCategoryPath() {
        assertThatThrownBy(() -> client.fetchCategoryPage("/busca/rtx", 1))
                .isInstanceOf(DisallowedUrlException.class);
    }
}
