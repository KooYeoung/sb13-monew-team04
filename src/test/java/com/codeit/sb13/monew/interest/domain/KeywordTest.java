package com.codeit.sb13.monew.interest.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KeywordTest {

    @Test
    @DisplayName("changeKeyword()로 키워드 텍스트를 변경할 수 있다")
    void changeKeyword() {
        // given
        Interest interest = Interest.create("스포츠");
        Keyword keyword = interest.addKeyword("축구");

        // when
        keyword.changeKeyword("풋살");

        // then
        assertThat(keyword.getKeyword()).isEqualTo("풋살");
    }
}
