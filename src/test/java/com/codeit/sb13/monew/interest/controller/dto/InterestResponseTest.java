package com.codeit.sb13.monew.interest.controller.dto;

import com.codeit.sb13.monew.interest.domain.Interest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InterestResponseTest {

    @Nested
    @DisplayName("of()")
    class Of {

        @Test
        @DisplayName("Interest 엔티티와 구독 정보를 응답 DTO로 변환한다.")
        void of_toResponse() {
            // given
            Interest interest = Interest.create("스포츠");
            interest.addKeyword("축구");
            interest.addKeyword("야구");

            // when
            InterestResponse response = InterestResponse.of(interest, 3L, true);

            // then
            assertThat(response.id()).isEqualTo(interest.getId());
            assertThat(response.name()).isEqualTo(interest.getName());
            assertThat(response.keywords()).containsExactly("축구", "야구");
            assertThat(response.subscriberCount()).isEqualTo(3L);
            assertThat(response.subscribedByMe()).isTrue();
            assertThat(response.createdAt()).isEqualTo(interest.getCreatedAt());
        }

        @Test
        @DisplayName("키워드가 없는 관심사는 빈 목록으로 변환된다.")
        void of_withNoKeyWords() {
            // given
            Interest interest = Interest.create("여행");

            // when
            InterestResponse response = InterestResponse.of(interest, 0L, false);

            // then
            assertThat(response.keywords()).isEmpty();
        }
    }
}