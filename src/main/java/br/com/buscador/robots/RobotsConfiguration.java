package br.com.buscador.robots;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * As regras são específicas da KaBuM. Quando outra fonte precisar de guarda
 * própria, ela ganha o seu bean, não uma variação desta.
 */
@Configuration
public class RobotsConfiguration {

    @Bean
    RobotsGuard robotsGuard() {
        return new RobotsGuard(RobotsRules.kabum());
    }
}
