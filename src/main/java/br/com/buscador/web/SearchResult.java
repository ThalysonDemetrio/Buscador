package br.com.buscador.web;

import java.util.List;

/**
 * coveredCategories existe para atender o requisito da spec de não deixar
 * "nenhum resultado" parecer "a loja não tem o produto": a cobertura é
 * parcial por construção, e a interface precisa dizer isso.
 */
public record SearchResult(
        List<OfferView> offers,
        List<String> failedSources,
        List<String> coveredCategories
) {}
