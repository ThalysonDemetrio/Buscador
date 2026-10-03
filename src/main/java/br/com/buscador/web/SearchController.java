package br.com.buscador.web;

import br.com.buscador.history.PriceChange;
import br.com.buscador.history.PriceHistory;
import br.com.buscador.kabum.KabumProperties;
import br.com.buscador.offer.Offer;
import br.com.buscador.offer.OfferProvider;
import br.com.buscador.search.RelevanceOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@RestController
public class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    private final List<OfferProvider> providers;
    private final PriceHistory history;
    private final KabumProperties kabumProperties;

    public SearchController(List<OfferProvider> providers, PriceHistory history,
                            KabumProperties kabumProperties) {
        this.providers = providers;
        this.history = history;
        this.kabumProperties = kabumProperties;
    }

    @GetMapping("/api/search")
    public SearchResult search(@RequestParam String term) {
        if (term == null || term.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "informe um termo de busca");
        }
        List<Offer> offers = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<List<Offer>>> tasks = providers.stream()
                    .map(p -> (Callable<List<Offer>>) () -> p.search(term))
                    .toList();
            List<Future<List<Offer>>> futures = executor.invokeAll(tasks);
            for (int i = 0; i < futures.size(); i++) {
                try {
                    offers.addAll(futures.get(i).get());
                } catch (Exception e) {
                    String label = providers.get(i).source().label();
                    log.warn("fonte {} falhou: {}", label, e.getMessage());
                    failed.add(label);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "busca interrompida");
        }

        Map<Offer, PriceChange> changes = history.changesFor(offers);
        List<OfferView> views = offers.stream()
                .sorted(RelevanceOrder.forTerm(term))
                .map(o -> OfferView.of(o, changes.get(o)))
                .toList();
        history.record(offers);
        return new SearchResult(views, failed, kabumProperties.categories().keySet().stream().toList());
    }
}
