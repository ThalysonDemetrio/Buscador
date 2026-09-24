package br.com.buscador.kabum;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * O builder vem autoconfigurado pelo starter de RestClient, que é o que traz
 * os timeouts de spring.http.clients. Construir o RestClient à mão aqui
 * devolveria um cliente sem timeout nenhum.
 */
@Configuration
public class KabumHttpConfiguration {

    @Bean
    RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }
}
