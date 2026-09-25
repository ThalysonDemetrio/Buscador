package br.com.buscador.search;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Forma canônica de texto para busca, compartilhada por todas as fontes:
 * ninguém digita acento, então "memoria" precisa achar "Memória". Fica em Java
 * de propósito — lower() e LIKE do SQLite só fazem case-fold de ASCII.
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    public static List<String> words(String text) {
        return Arrays.stream(normalize(text).split("\\s+"))
                .filter(w -> !w.isBlank())
                .toList();
    }
}
