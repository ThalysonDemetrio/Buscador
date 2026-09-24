package br.com.buscador.robots;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RobotsGuardTest {

    private final RobotsGuard guard = new RobotsGuard(RobotsRules.kabum());

    @Test
    void allowsPlainCategoryPage() {
        assertThat(guard.isAllowed("https://www.kabum.com.br/hardware/memoria-ram"))
                .isTrue();
    }

    @Test
    void allowsCategoryPageWithPageNumber() {
        assertThat(guard.isAllowed(
                "https://www.kabum.com.br/hardware/memoria-ram?page_number=2"))
                .isTrue();
    }

    @Test
    void blocksInternalCatalogApiBecauseOfSortParameter() {
        assertThat(guard.isAllowed("https://www.kabum.com.br/catalog/v2/"
                + "products-by-category/hardware/memoria-ram"
                + "?sort=most_searched&page_number=1")).isFalse();
    }

    @Test
    void blocksSearchEndpoint() {
        assertThat(guard.isAllowed(
                "https://www.kabum.com.br/busca/memoria-ram?facet=x")).isFalse();
    }

    @Test
    void blocksQueryParameter() {
        assertThat(guard.isAllowed(
                "https://www.kabum.com.br/hardware?query=rtx")).isFalse();
    }

    @Test
    void ensureAllowedThrowsOnDisallowedUrl() {
        assertThatThrownBy(() -> guard.ensureAllowed(
                "https://www.kabum.com.br/hardware?query=rtx"))
                .isInstanceOf(DisallowedUrlException.class)
                .hasMessageContaining("query=");
    }

    @Test
    void ensureAllowedPassesOnAllowedUrl() {
        guard.ensureAllowed("https://www.kabum.com.br/hardware/memoria-ram");
    }
}
