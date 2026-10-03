package br.com.buscador.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogRefreshScheduleTest {

    private final CountingRefresher refresher = new CountingRefresher();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(CatalogRefresher.class, () -> refresher)
            .withUserConfiguration(CatalogRefreshSchedule.class)
            .withPropertyValues("buscador.catalog.refresh.check-interval=PT1H");

    @Test
    void refreshesAsSoonAsTheApplicationStarts() {
        runner.run(context -> assertThat(refresher.calls.await(5, TimeUnit.SECONDS)).isTrue());
    }

    @Test
    void canBeTurnedOffSoTestsNeverReachTheRealStore() {
        runner.withPropertyValues("buscador.catalog.refresh.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(CatalogRefreshSchedule.class));
    }

    private static class CountingRefresher extends CatalogRefresher {
        final CountDownLatch calls = new CountDownLatch(1);

        CountingRefresher() {
            super(null, null, null, null);
        }

        @Override
        public void refreshStaleCategories() {
            calls.countDown();
        }
    }
}
