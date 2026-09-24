package br.com.buscador.robots;

import java.util.List;

/**
 * Regras deliberadamente mais estritas que https://www.kabum.com.br/robots.txt (2026-09-24).
 * Cada entrada é um trecho que, se presente na URL, a torna proibida.
 *
 * O arquivo original enumera valores específicos de parâmetros (e.g., sort=most_searched).
 * Nós bloqueamos parâmetros inteiros (e.g., sort=) porque:
 * - O cliente nunca os usa e não precisa deles
 * - Deixar passar é falha silenciosa com risco real (acesso indevido)
 * - Falso positivo é barato (apenas barulhento)
 * - Se a loja adicionar novos valores no futuro, escapariam da lista específica
 */
public record RobotsRules(List<String> disallowedFragments) {

    public static RobotsRules kabum() {
        return new RobotsRules(List.of(
                "/busca",
                "query=",
                "sort=",
                "/precarrinho",
                "/carrinho",
                "/minha-conta",
                "/login",
                "/manager/",
                "/destaques",
                "/lancamentos"));
    }
}
