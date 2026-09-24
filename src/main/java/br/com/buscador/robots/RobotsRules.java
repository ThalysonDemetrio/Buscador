package br.com.buscador.robots;

import java.util.List;

/**
 * Regras transcritas de https://www.kabum.com.br/robots.txt em 2026-09-24.
 * Cada entrada é um trecho que, se presente na URL, a torna proibida.
 */
public record RobotsRules(List<String> disallowedFragments) {

    public static RobotsRules kabum() {
        return new RobotsRules(List.of(
                "/busca/",
                "query=",
                "sort=most_searched",
                "sort=price",
                "sort=-price",
                "sort=-offer_products",
                "sort=manufacturer_name",
                "sort=-date_product_arrived",
                "sort=-number_ratings",
                "/precarrinho",
                "/carrinho",
                "/minha-conta",
                "/login",
                "/manager/",
                "/destaques",
                "/lancamentos"));
    }
}
