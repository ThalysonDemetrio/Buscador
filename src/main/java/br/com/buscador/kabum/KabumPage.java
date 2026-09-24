package br.com.buscador.kabum;

import java.util.List;

/**
 * @param discardedCount quantos produtos desta página foram descartados pelo
 *                        parser por serem individualmente inválidos (preço
 *                        não numérico, código ausente, etc). Não é erro de
 *                        formato — esse aborta o parse inteiro — é produto
 *                        pontual ruim. Quem exibe o resultado precisa desse
 *                        número para avisar que a lista veio incompleta, em
 *                        vez de deixar parecer que a categoria só tinha
 *                        {@code products.size()} itens.
 */
public record KabumPage(List<KabumProduct> products, int totalPages, int discardedCount) {}
