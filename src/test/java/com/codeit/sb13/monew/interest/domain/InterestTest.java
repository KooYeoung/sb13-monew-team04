package com.codeit.sb13.monew.interest.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.codeit.sb13.monew.global.exception.interest.InterestKeywordInvalidException;
import com.codeit.sb13.monew.global.exception.interest.InterestKeywordRequiredException;
import com.codeit.sb13.monew.global.exception.interest.InterestNameInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InterestTest {

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("이름을 받아 관심사를 생성하면, 이름이 채워지고 키워드 목록은 비어있다")
        void create_setsNameAndEmptyKeywords() {
            // given & when
            Interest interest = Interest.create("스포츠");

            // then
            assertThat(interest.getName()).isEqualTo("스포츠");
            assertThat(interest.getKeywords()).isEmpty();
        }

        @Test
        @DisplayName("이름이 null이면 예외가 발생한다")
        void create_nullName_throwsException() {
            // when & then
            assertThatThrownBy(() -> Interest.create(null))
                    .isInstanceOf(InterestNameInvalidException.class);
        }

        @Test
        @DisplayName("이름이 비어 있거나 공백이면 예외가 발생한다")
        void create_blankName_throwsException() {
            // when & then
            assertThatThrownBy(() -> Interest.create(""))
                    .isInstanceOf(InterestNameInvalidException.class);
            assertThatThrownBy(() -> Interest.create("   "))
                    .isInstanceOf(InterestNameInvalidException.class);
        }

        @Test
        @DisplayName("이름이 50자면 생성되고 51자를 넘으면 예외가 발생한다")
        void create_nameLengthBoundary() {
            // given
            String maxLength = "가".repeat(50);
            String tooLong = "가".repeat(51);

            // when
            Interest interest = Interest.create(maxLength);

            // then
            assertThat(interest.getName()).isEqualTo(maxLength);
            assertThatThrownBy(() -> Interest.create(tooLong))
                    .isInstanceOf(InterestNameInvalidException.class);
        }
    }

    @Nested
    @DisplayName("addKeyword()")
    class AddKeyword {

        @Test
        @DisplayName("키워드를 추가하면 관심사의 키워드 목록에 담긴다")
        void addKeyword_addsToKeywordList() {
            // given
            Interest interest = Interest.create("스포츠");

            // when
            Keyword keyword = interest.addKeyword("축구");

            // then
            assertThat(interest.getKeywords())
                    .hasSize(1)
                    .contains(keyword);
        }

        @Test
        @DisplayName("추가된 키워드는 자신을 추가한 관심사를 양방향으로 참조한다")
        void addKeyword_setsBidirectionalReference() {
            // given
            Interest interest = Interest.create("스포츠");

            // when
            Keyword keyword = interest.addKeyword("축구");

            // then
            assertThat(keyword.getInterest()).isEqualTo(interest);
            assertThat(keyword.getKeyword()).isEqualTo("축구");
        }

        @Test
        @DisplayName("키워드를 여러 개 추가하면 추가한 순서/개수만큼 쌓인다")
        void addKeyword_multipleTimes_accumulates() {
            // given
            Interest interest = Interest.create("스포츠");

            // when
            interest.addKeyword("축구");
            interest.addKeyword("야구");
            interest.addKeyword("농구");

            // then
            List<String> keywordTexts = interest.getKeywords().stream()
                    .map(Keyword::getKeyword)
                    .toList();

            assertThat(keywordTexts).containsExactly("축구", "야구", "농구");
        }

        @Test
        @DisplayName("키워드가 null이면 예외가 발생하고 목록은 그대로 유지된다")
        void addKeyword_null_throwsException() {
            // given
            Interest interest = Interest.create("스포츠");

            // when & then
            assertThatThrownBy(() -> interest.addKeyword(null))
                    .isInstanceOf(InterestKeywordInvalidException.class);
            assertThat(interest.getKeywords()).isEmpty();
        }

        @Test
        @DisplayName("키워드가 비어 있거나 공백이면 예외가 발생한다")
        void addKeyword_blank_throwsException() {
            // given
            Interest interest = Interest.create("스포츠");

            // when & then
            assertThatThrownBy(() -> interest.addKeyword(""))
                    .isInstanceOf(InterestKeywordInvalidException.class);
            assertThatThrownBy(() -> interest.addKeyword("   "))
                    .isInstanceOf(InterestKeywordInvalidException.class);
            assertThat(interest.getKeywords()).isEmpty();
        }

        @Test
        @DisplayName("키워드가 50자면 추가되고 51자를 넘으면 예외가 발생한다")
        void addKeyword_lengthBoundary() {
            // given
            Interest interest = Interest.create("스포츠");
            String maxLength = "가".repeat(50);
            String tooLong = "가".repeat(51);

            // when
            Keyword keyword = interest.addKeyword(maxLength);

            // then
            assertThat(keyword.getKeyword()).isEqualTo(maxLength);
            assertThatThrownBy(() -> interest.addKeyword(tooLong))
                    .isInstanceOf(InterestKeywordInvalidException.class);
            assertThat(interest.getKeywords()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("removeKeyword()")
    class RemoveKeyword {

        @Test
        @DisplayName("키워드를 제거하면 관심사의 키워드 목록에서 사라진다")
        void removeKeyword_removesFromKeywordList() {
            // given
            Interest interest = Interest.create("스포츠");
            Keyword football = interest.addKeyword("축구");
            interest.addKeyword("야구");

            // when
            interest.removeKeyword(football);

            // then
            assertThat(interest.getKeywords().size()).isEqualTo(1);
            assertThat(interest.getKeywords()).doesNotContain(football);
        }

        @Test
        @DisplayName("여러 키워드 중 하나만 제거하면 나머지는 그대로 남는다")
        void removeKeyword_removesOnlyTargetKeyword() {
            // given
            Interest interest = Interest.create("스포츠");
            Keyword football = interest.addKeyword("축구");
            interest.addKeyword("야구");

            // when
            interest.removeKeyword(football);

            // then
            assertThat(interest.getKeywords())
                    .hasSize(1)
                    .doesNotContain(football);
        }

        @Test
        @DisplayName("키워드를 제거하면 제거된 키워드의 interest 참조도 끊어진다")
        void removeKeyword_detachesInterestReference() {
            // given
            Interest interest = Interest.create("스포츠");
            Keyword keyword = interest.addKeyword("축구");
            interest.addKeyword("야구");

            // when
            interest.removeKeyword(keyword);

            // then
            assertThat(keyword.getInterest()).isEqualTo(null);
        }

        @Test
        @DisplayName("소속되지 않은 키워드를 제거해도 무시되며, 원래 소속의 참조는 유지된다")
        void removeKeyword_notContained_doesNotDetach() {
            // given
            Interest interest = Interest.create("스포츠");
            Interest other = Interest.create("음악");
            Keyword otherKeyword = other.addKeyword("재즈");

            // when
            interest.removeKeyword(otherKeyword);

            // then
            assertThat(otherKeyword.getInterest()).isEqualTo(other);
            assertThat(other.getKeywords()).contains(otherKeyword);
        }

        @Test
        @DisplayName("영속화되지 않은 관심사라도 마지막 남은 키워드를 제거하려 하면 예외가 발생하고 키워드는 그대로 남는다")
        void removeKeyword_lastOne_throwsException() {
            // given
            Interest interest = Interest.create("스포츠");
            Keyword keyword = interest.addKeyword("축구");

            // when & then
            assertThatThrownBy(() -> interest.removeKeyword(keyword))
                    .isInstanceOf(InterestKeywordRequiredException.class);
            assertThat(interest.getKeywords()).containsExactly(keyword);
            assertThat(keyword.getInterest()).isEqualTo(interest);
        }
    }

    @Nested
    @DisplayName("changeName()")
    class ChangeName {

        @Test
        @DisplayName("이름을 변경하면 새 이름으로 바뀐다")
        void changeName_updatesName() {
            // given
            Interest interest = Interest.create("스포츠");

            // when
            interest.changeName("야구");

            // then
            assertThat(interest.getName()).isEqualTo("야구");
        }

        @Test
        @DisplayName("null로 변경하려 하면 예외가 발생하고 기존 이름은 유지된다")
        void changeName_null_throwsException() {
            // given
            Interest interest = Interest.create("스포츠");

            // when & then
            assertThatThrownBy(() -> interest.changeName(null))
                    .isInstanceOf(InterestNameInvalidException.class);
            assertThat(interest.getName()).isEqualTo("스포츠");
        }

        @Test
        @DisplayName("빈 문자열이나 공백 문자열로 변경하려 하면 예외가 발생한다")
        void changeName_blank_throwsException() {
            // given
            Interest interest = Interest.create("스포츠");

            // when & then
            assertThatThrownBy(() -> interest.changeName(""))
                    .isInstanceOf(InterestNameInvalidException.class);
            assertThatThrownBy(() -> interest.changeName("   "))
                    .isInstanceOf(InterestNameInvalidException.class);
            assertThat(interest.getName()).isEqualTo("스포츠");
        }

        @Test
        @DisplayName("50자는 허용되지만 51자를 넘으면 예외가 발생한다")
        void changeName_lengthBoundary() {
            // given
            Interest interest = Interest.create("스포츠");
            String maxLength = "가".repeat(50);
            String tooLong = "가".repeat(51);

            // when
            interest.changeName(maxLength);

            // then
            assertThat(interest.getName()).isEqualTo(maxLength);
            assertThatThrownBy(() -> interest.changeName(tooLong))
                    .isInstanceOf(InterestNameInvalidException.class);
        }
    }
}
