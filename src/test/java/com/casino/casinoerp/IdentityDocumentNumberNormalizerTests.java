package com.casino.casinoerp;

import com.casino.casinoerp.service.IdentityDocumentNumberNormalizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityDocumentNumberNormalizerTests {

    private final IdentityDocumentNumberNormalizer normalizer = new IdentityDocumentNumberNormalizer();

    @Test
    void removesSpacesAndHyphensAndNormalizesCase() {
        assertThat(normalizer.normalize("  ab-123 456  ")).isEqualTo("AB123456");
        assertThat(normalizer.normalize(" ab 123 ")).isEqualTo("AB123");
    }

    @Test
    void appliesUnicodeNfkcNormalization() {
        assertThat(normalizer.normalize("ＡＢ－１２３")).isEqualTo("AB123");
    }

    @Test
    void preservesOtherPunctuation() {
        assertThat(normalizer.normalize("ab/12.3")).isEqualTo("AB/12.3");
    }

    @Test
    void rejectsBlankNormalizedValue() {
        assertThatThrownBy(() -> normalizer.normalize(" - \t - "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Document number is required.");
    }

    @Test
    void rejectsNullValue() {
        assertThatThrownBy(() -> normalizer.normalize(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Document number is required.");
    }
}
