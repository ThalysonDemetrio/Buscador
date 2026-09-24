package br.com.buscador.kabum;

import java.util.List;

public record KabumPage(List<KabumProduct> products, int totalPages) {}
