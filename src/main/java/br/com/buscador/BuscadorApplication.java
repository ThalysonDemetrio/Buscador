package br.com.buscador;

import br.com.buscador.robots.RobotsGuard;
import br.com.buscador.robots.RobotsRules;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BuscadorApplication {

	public static void main(String[] args) {
		SpringApplication.run(BuscadorApplication.class, args);
	}

	@Bean
	RobotsGuard robotsGuard() {
		return new RobotsGuard(RobotsRules.kabum());
	}

	@Bean
	RestClient restClient() {
		return RestClient.builder().build();
	}

}
