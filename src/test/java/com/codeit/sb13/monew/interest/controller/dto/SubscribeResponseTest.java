package com.codeit.sb13.monew.interest.controller.dto;

import com.codeit.sb13.monew.global.config.QueryDslConfig;
import com.codeit.sb13.monew.interest.domain.Interest;
import com.codeit.sb13.monew.interest.domain.Subscribe;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code Subscribe.getCreatedAt()}은 {@code @CreatedDate} 기반이라 영속화 전에는 항상
 * {@code null}이다. 영속화하지 않은 fixture로만 검증하면 {@code null}과 {@code null}을
 * 비교하는 셈이라 매핑이 실제로 값을 잘 옮기는지 증명하지 못한다. 그래서
 * {@code @DataJpaTest}로 Interest와 Subscribe를 실제로 저장해, id와 createdAt이
 * 채워진 상태에서 {@link SubscribeResponse#of}의 매핑을 검증한다.
 */
@DataJpaTest
@Import(QueryDslConfig.class)
@ActiveProfiles("test")
class SubscribeResponseTest {

    @Autowired
    private TestEntityManager em;

    @Nested
    @DisplayName("of()")
    class Of {

        @Test
        @DisplayName("영속화된 Subscribe 엔티티와 구독자 수를 응답 DTO로 변환한다.")
        void of_toResponse() {
            // given
            Interest interest = Interest.create("스포츠");
            interest.addKeyword("축구");
            interest.addKeyword("야구");
            em.persistAndFlush(interest);

            Subscribe subscribe = Subscribe.of(interest, UUID.randomUUID());
            em.persistAndFlush(subscribe);

            assertThat(subscribe.getId()).isNotNull();
            assertThat(subscribe.getCreatedAt()).isNotNull();

            // when
            SubscribeResponse response = SubscribeResponse.of(subscribe, 3L);

            // then
            assertThat(response.id()).isEqualTo(subscribe.getId());
            assertThat(response.interestId()).isEqualTo(interest.getId());
            assertThat(response.interestName()).isEqualTo(interest.getName());
            assertThat(response.interestKeywords()).containsExactly("축구", "야구");
            assertThat(response.interestSubscriberCount()).isEqualTo(3L);
            assertThat(response.createdAt()).isEqualTo(subscribe.getCreatedAt());
        }

        @Test
        @DisplayName("키워드가 없는 관심사를 구독하면 빈 목록으로 변환된다.")
        void of_withNoKeywords() {
            // given
            Interest interest = Interest.create("여행");
            em.persistAndFlush(interest);

            Subscribe subscribe = Subscribe.of(interest, UUID.randomUUID());
            em.persistAndFlush(subscribe);

            // when
            SubscribeResponse response = SubscribeResponse.of(subscribe, 0L);

            // then
            assertThat(response.interestKeywords()).isEmpty();
        }
    }
}
