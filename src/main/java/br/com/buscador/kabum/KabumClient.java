package br.com.buscador.kabum;

import br.com.buscador.robots.RobotsGuard;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class KabumClient {

    private final RestClient restClient;
    private final RobotsGuard guard;
    private final KabumPayloadParser parser;
    private final KabumProperties properties;

    private Long lastRequestNanos;

    public KabumClient(RestClient restClient, RobotsGuard guard,
                       KabumPayloadParser parser, KabumProperties properties) {
        this.restClient = restClient;
        this.guard = guard;
        this.parser = parser;
        this.properties = properties;
    }

    public KabumPage fetchCategoryPage(String categoryPath, int pageNumber) {
        String url = buildUrl(categoryPath, pageNumber);
        guard.ensureAllowed(url);
        waitForTurn();
        String html = restClient.get()
                .uri(url)
                .header("User-Agent", properties.userAgent())
                .retrieve()
                .body(String.class);
        if (html == null) {
            throw new KabumPayloadException("resposta vazia para " + url);
        }
        return parser.parse(html);
    }

    /** Garante o intervalo mínimo entre duas requisições à loja, venham de onde vierem. */
    private synchronized void waitForTurn() {
        if (lastRequestNanos != null) {
            Duration elapsed = Duration.ofNanos(System.nanoTime() - lastRequestNanos);
            Duration remaining = properties.requestInterval().minus(elapsed);
            if (remaining.isPositive()) {
                try {
                    Thread.sleep(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrompido aguardando a vez de acessar a loja", e);
                }
            }
        }
        lastRequestNanos = System.nanoTime();
    }

    private String buildUrl(String categoryPath, int pageNumber) {
        String url = properties.baseUrl() + categoryPath;
        return pageNumber <= 1 ? url : url + "?page_number=" + pageNumber;
    }
}
