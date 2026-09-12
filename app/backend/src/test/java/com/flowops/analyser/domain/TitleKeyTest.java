package com.flowops.analyser.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("ANALYSER-RUN-01")
class TitleKeyTest {
    private static final String COMPOSED = "ședință de lucru";

    private static String decomposed(String composed) {
        return Normalizer.normalize(composed, Normalizer.Form.NFD);
    }

    @Test
    void caseIsNotPartOfTheKey() {
        assertThat(TitleKey.of("Caption Set")).isEqualTo("caption set");
        assertThat(TitleKey.of("CAPTION SET")).isEqualTo("caption set");
    }

    @Test
    void whitespaceAroundATitleIsNotPartOfTheKey() {
        assertThat(TitleKey.of("  caption set ")).isEqualTo("caption set");
    }

    @Test
    void repeatedInternalSpacesCollapseToOne() {
        assertThat(TitleKey.of("captions  set")).isEqualTo("captions set");
        assertThat(TitleKey.of("captions     set")).isEqualTo("captions set");
    }

    @Test
    void tabsAndNewlinesAreCollapsedLikeSpaces() {
        assertThat(TitleKey.of("caption\tset")).isEqualTo("caption set");
        assertThat(TitleKey.of("caption\nset")).isEqualTo("caption set");
        assertThat(TitleKey.of("\n caption \t\n set \t")).isEqualTo("caption set");
    }

    @Test
    void aNullTitleStaysNull() {
        assertThat(TitleKey.of(null)).isNull();
    }

    @Test
    void aBlankTitleIsTreatedAsUnanswered() {
        assertThat(TitleKey.of("")).isNull();
        assertThat(TitleKey.of("   ")).isNull();
        assertThat(TitleKey.of("\t\n ")).isNull();
    }

    @Test
    void aDecomposedRomanianTitleNormalisesToTheSameKeyAsItsComposedForm() {
        String split = decomposed(COMPOSED);

        assertThat(split).isNotEqualTo(COMPOSED);
        assertThat(TitleKey.of(split)).isEqualTo(COMPOSED);
        assertThat(TitleKey.of(COMPOSED)).isEqualTo(COMPOSED);
        assertThat(TitleKey.sameTitle(split, COMPOSED)).isTrue();
    }

    @Test
    void aDecomposedCapitalDiacriticStillReachesTheLowerCaseComposedKey() {
        assertThat(TitleKey.of(decomposed("Ședință De Lucru"))).isEqualTo(COMPOSED);
    }

    @Test
    void twoTitlesThatDifferOnlyByCaseAndSpacingAreTheSameTitle() {
        assertThat(TitleKey.sameTitle("caption set", "  Caption   Set ")).isTrue();
    }

    @Test
    void twoDifferentTitlesAreNotTheSameTitle() {
        assertThat(TitleKey.sameTitle("caption set", "caption sets")).isFalse();
    }

    @Test
    void twoUntitledNodesAreNotTheSameTitle() {
        assertThat(TitleKey.sameTitle(null, null)).isFalse();
        assertThat(TitleKey.sameTitle("   ", "")).isFalse();
        assertThat(TitleKey.sameTitle("caption set", null)).isFalse();
        assertThat(TitleKey.sameTitle(null, "caption set")).isFalse();
    }

    @Test
    void aNormaliserDoesNotMergeSynonyms() {
        assertThat(TitleKey.sameTitle("caption set", "post copy")).isFalse();
        assertThat(TitleKey.sameTitle("caption set", "captions for the set")).isFalse();
    }
}
