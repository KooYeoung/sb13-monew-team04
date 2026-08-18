package com.codeit.sb13.monew.interest.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import com.codeit.sb13.monew.global.exception.interest.InterestKeywordRequiredException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
        @DisplayName("마지막 남은 키워드를 제거하려 하면 예외가 발생하고 키워드는 그대로 남는다")
        void removeKeyword_lastOne_throwsException() {
            // given
            Interest interest = Interest.create("스포츠");
            ReflectionTestUtils.setField(interest, "id", UUID.randomUUID());
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
    }
}
