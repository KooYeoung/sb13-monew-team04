package com.codeit.sb13.monew.interest.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeit.sb13.monew.global.exception.interest.InterestKeywordInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class KeywordTest {

    @Nested
    @DisplayName("changeKeyword()")
    class ChangeKeyword {

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

        @Test
        @DisplayName("null로 변경하려 하면 예외가 발생하고 기존 값은 유지된다")
        void changeKeyword_null_throwsException() {
            // given
            Interest interest = Interest.create("스포츠");
            Keyword keyword = interest.addKeyword("축구");

            // when & then
            assertThatThrownBy(() -> keyword.changeKeyword(null))
                    .isInstanceOf(InterestKeywordInvalidException.class);
            assertThat(keyword.getKeyword()).isEqualTo("축구");
        }

        @Test
        @DisplayName("빈 문자열이나 공백 문자열로 변경하려 하면 예외가 발생한다")
        void changeKeyword_blank_throwsException() {
            // given
            Interest interest = Interest.create("스포츠");
            Keyword keyword = interest.addKeyword("축구");

            // when & then
            assertThatThrownBy(() -> keyword.changeKeyword(""))
                    .isInstanceOf(InterestKeywordInvalidException.class);
            assertThatThrownBy(() -> keyword.changeKeyword("   "))
                    .isInstanceOf(InterestKeywordInvalidException.class);
            assertThat(keyword.getKeyword()).isEqualTo("축구");
        }

        @Test
        @DisplayName("50자는 허용되지만 51자는 예외가 발생한다")
        void changeKeyword_lengthBoundary() {
            // given
            Interest interest = Interest.create("스포츠");
            Keyword keyword = interest.addKeyword("축구");
            String maxLength = "가".repeat(50);
            String tooLong = "가".repeat(51);

            // when
            keyword.changeKeyword(maxLength);

            // then
            assertThat(keyword.getKeyword()).isEqualTo(maxLength);
            assertThatThrownBy(() -> keyword.changeKeyword(tooLong))
                    .isInstanceOf(InterestKeywordInvalidException.class);
        }
    }
}
