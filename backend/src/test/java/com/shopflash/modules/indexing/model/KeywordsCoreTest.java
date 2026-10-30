package com.shopflash.modules.indexing.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KeywordsCoreTest {

    @Test
    void splitsLowercasesAndKeepsVietnamese() {
        assertThat(Keywords.parse("Tai nghe SONY Pro")).containsExactly("tai", "nghe", "sony", "pro");
        assertThat(Keywords.parse("  Điện   thoại ")).containsExactly("điện", "thoại");
    }

    @Test
    void dropsShortWordsStopWordsAndDuplicates() {
        assertThat(Keywords.parse("a b pro and max PRO")).containsExactly("pro", "max");
        assertThat(Keywords.parse("near the sony")).containsExactly("sony");
    }

    @Test
    void removesQuerySyntaxCharacters() {
        assertThat(Keywords.parse("@name:{x}|(evil)* -\"drop\"")).containsExactly("name", "evil", "drop");
        assertThat(Keywords.parse("pro%' OR 1=1 --")).containsExactly("pro");
    }

    @Test
    void keepsAtMostFiveWords() {
        assertThat(Keywords.parse("a1 b2 c3 d4 e5 f6")).containsExactly("a1", "b2", "c3", "d4", "e5");
    }

    @Test
    void emptyInput() {
        assertThat(Keywords.parse(null)).isEmpty();
        assertThat(Keywords.parse("   ")).isEmpty();
        assertThat(Keywords.parse("!!! ?")).isEmpty();
    }
}
