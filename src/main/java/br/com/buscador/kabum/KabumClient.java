package br.com.buscador.kabum;

import br.com.buscador.robots.RobotsGuard;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KabumClient {

    private final RestClient restClient;
    private final RobotsGuard guard;
    private final KabumPayloadParser parser;
    private final KabumProperties properties;

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

    private String buildUrl(String categoryPath, int pageNumber) {
        String url = properties.baseUrl() + categoryPath;
        return pageNumber <= 1 ? url : url + "?page_number=" + pageNumber;
    }
}
