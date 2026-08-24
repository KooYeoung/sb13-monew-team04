package com.codeit.sb13.monew.interest.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.monew.global.config.QueryDslConfig;
import com.codeit.sb13.monew.interest.domain.Interest;
import com.codeit.sb13.monew.interest.domain.Subscribe;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import(QueryDslConfig.class)
@ActiveProfiles("test")
class SubscribeRepositoryTest {

    @Autowired
    private SubscribeRepository subscribeRepository;

    @Autowired
    private TestEntityManager em;

    @Nested
    @DisplayName("findByInterest_IdAndUserId()")
    class FindByInterestIdAndUserId {

        @Test
        @DisplayName("이미 구독 중이면 그 구독을 반환한다")
        void existingSubscription_returnsIt() {
            // given
            Interest interest = Interest.create("스포츠");
            interest.addKeyword("축구");
            em.persistAndFlush(interest);

            UUID userId = UUID.randomUUID();
            Subscribe subscribe = Subscribe.of(interest, userId);
            em.persistAndFlush(subscribe);

            // when
            Optional<Subscribe> found =
                    subscribeRepository.findByInterest_IdAndUserId(interest.getId(), userId);

            // then
            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(subscribe.getId());
        }

        @Test
        @DisplayName("구독한 적이 없으면 빈 값을 반환한다")
        void notSubscribed_returnsEmpty() {
            // given
            Interest interest = Interest.create("스포츠");
            interest.addKeyword("축구");
            em.persistAndFlush(interest);

            // when
            Optional<Subscribe> found =
                    subscribeRepository.findByInterest_IdAndUserId(interest.getId(), UUID.randomUUID());

            // then
            assertThat(found).isEmpty();
        }
    }

    @Test
    @DisplayName("countByInterest_Id()는 해당 관심사를 구독 중인 사용자 수만 센다")
    void countByInterest_Id_countsOnlyThatInterestsSubscriptions() {
        // given
        Interest target = Interest.create("스포츠");
        target.addKeyword("축구");
        em.persistAndFlush(target);

        Interest other = Interest.create("여행");
        other.addKeyword("국내여행");
        em.persistAndFlush(other);

        em.persistAndFlush(Subscribe.of(target, UUID.randomUUID()));
        em.persistAndFlush(Subscribe.of(target, UUID.randomUUID()));
        em.persistAndFlush(Subscribe.of(other, UUID.randomUUID()));

        // when
        long count = subscribeRepository.countByInterest_Id(target.getId());

        // then
        assertThat(count).isEqualTo(2L);
    }
}
