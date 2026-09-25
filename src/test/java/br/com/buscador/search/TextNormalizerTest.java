package br.com.buscador.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizerTest {

    @Test
    void removesAccentsAndLowercases() {
        assertThat(TextNormalizer.normalize("Memória PLACA DE VÍDEO"))
                .isEqualTo("memoria placa de video");
    }

    @Test
    void splitsIntoWordsIgnoringExtraWhitespace() {
        assertThat(TextNormalizer.words("  Placa   de\tVídeo "))
                .containsExactly("placa", "de", "video");
    }

    @Test
    void blankTextHasNoWords() {
        assertThat(TextNormalizer.words("   ")).isEmpty();
    }
}
