package br.com.buscador.offer;

public enum Source {
    KABUM("KaBuM!"),
    MERCADO_LIVRE("Mercado Livre");

    private final String label;

    Source(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
