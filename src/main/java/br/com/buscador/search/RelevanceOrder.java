package br.com.buscador.search;

import br.com.buscador.offer.Offer;

import java.util.Comparator;

/**
 * Ordem de exibição: título que começa com o termo buscado primeiro, depois
 * custo efetivo crescente.
 *
 * <p>Ordenar só por preço trazia acessórios ("Suporte para Placa de Vídeo",
 * R$ 24,99) antes do produto. O nome do produto costuma abrir o título,
 * enquanto acessórios citam o produto no meio. O sinal é binário de propósito:
 * dentro de cada grupo quem manda continua sendo o preço, que é o critério
 * de compra.
 */
public final class RelevanceOrder {

    private RelevanceOrder() {
    }

    public static Comparator<Offer> forTerm(String term) {
        String prefix = String.join(" ", TextNormalizer.words(term));
        Comparator<Offer> startsWithTerm = Comparator.comparing(
                offer -> !String.join(" ", TextNormalizer.words(offer.title()))
                        .startsWith(prefix));
        return startsWithTerm.thenComparing(Offer::effectiveCost);
    }
}
