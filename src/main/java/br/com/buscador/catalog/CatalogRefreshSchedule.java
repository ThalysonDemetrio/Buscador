package br.com.buscador.catalog;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Mantém o catálogo atualizado fora do caminho da busca: quem busca lê só o
 * SQLite e nunca espera a loja.
 *
 * <p>O agendador padrão tem uma única thread e o fixedDelay só conta depois
 * que a execução anterior termina, então duas atualizações nunca se
 * sobrepõem. A verificação é frequente, mas barata: só categorias vencidas
 * pelo cache-ttl vão até a loja.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "buscador.catalog.refresh", name = "enabled", matchIfMissing = true)
public class CatalogRefreshSchedule {

    private final CatalogRefresher refresher;

    public CatalogRefreshSchedule(CatalogRefresher refresher) {
        this.refresher = refresher;
    }

    @Scheduled(initialDelay = 0, fixedDelayString = "${buscador.catalog.refresh.check-interval}")
    void refreshStaleCategories() {
        refresher.refreshStaleCategories();
    }
}
