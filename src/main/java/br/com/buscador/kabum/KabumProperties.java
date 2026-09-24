package br.com.buscador.kabum;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "buscador.kabum")
public record KabumProperties(
        String baseUrl,
        String userAgent,
        Duration cacheTtl,
        List<String> categories
) {}
