package br.com.buscador.kabum;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "buscador.kabum")
public record KabumProperties(
        String baseUrl,
        String userAgent,
    Duration cacheTtl,
    Map<String, BigDecimal> categories
) {}
