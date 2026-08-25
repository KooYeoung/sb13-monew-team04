package com.codeit.sb13.monew.interest.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.groups.Tuple.tuple;

import com.codeit.sb13.monew.global.config.JpaAuditingConfig;
import com.codeit.sb13.monew.global.config.QueryDslConfig;
import com.codeit.sb13.monew.interest.domain.Interest;
import com.codeit.sb13.monew.interest.domain.Subscribe;
import com.codeit.sb13.monew.interest.repository.dto.InterestSubscriberRow;
import com.codeit.sb13.monew.interest.repository.dto.SubscribedInterestActivityProjection;
import com.codeit.sb13.monew.interest.service.dto.SubscribedInterestActivityDto;
import com.codeit.sb13.monew.user.domain.User;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@link SubscribeRepository}에 대한 통합 테스트.
 *
 * <p>{@code @DataJpaTest}는 {@link QueryDslConfig}나 {@link JpaAuditingConfig}처럼
 * 리포지토리/엔티티가 아닌 일반 {@code @Configuration}은 자동으로 스캔하지 않는다.
 * 현재 테스트 대상은 JPQL만 쓰지만, 같은 slice 안에서 {@code InterestRepository}의
 * QueryDSL 커스텀 구현도 함께 빈으로 만들어지므로 {@link QueryDslConfig}가 필요하다.
 * 또 구독 생성 시각을 검증하려면 {@code @CreatedDate}가 실제로 채워져야 하므로
 * {@link JpaAuditingConfig}도 함께 임포트한다.</p>
 */
@DataJpaTest
@Import({QueryDslConfig.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
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
            Interest interest = persistInterest("스포츠", "축구");

            UUID userId = UUID.randomUUID();
            Subscribe subscribe = Subscribe.of(interest, userId);
            em.persistAndFlush(subscribe);

            Optional<Subscribe> found =
                    subscribeRepository.findByInterest_IdAndUserId(interest.getId(), userId);

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(subscribe.getId());
        }

        @Test
        @DisplayName("구독한 적이 없으면 빈 값을 반환한다")
        void notSubscribed_returnsEmpty() {
            Interest interest = persistInterest("스포츠", "축구");

            Optional<Subscribe> found =
                    subscribeRepository.findByInterest_IdAndUserId(interest.getId(), UUID.randomUUID());

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("deleteByInterest_IdAndUserId()")
    class DeleteByInterestIdAndUserId {

        @Test
        @DisplayName("구독 중이면 해당 사용자의 구독만 삭제한다")
        void existingSubscription_deletesOnlyThatUsersSubscription() {
            Interest interest = persistInterest("스포츠", "축구");
            UUID targetUserId = UUID.randomUUID();
            UUID otherUserId = UUID.randomUUID();
            em.persistAndFlush(Subscribe.of(interest, targetUserId));
            em.persistAndFlush(Subscribe.of(interest, otherUserId));

            subscribeRepository.deleteByInterest_IdAndUserId(interest.getId(), targetUserId);
            em.flush();
            em.clear();

            assertThat(subscribeRepository.findByInterest_IdAndUserId(interest.getId(), targetUserId))
                    .isEmpty();
            assertThat(subscribeRepository.findByInterest_IdAndUserId(interest.getId(), otherUserId))
                    .isPresent();
        }

        @Test
        @DisplayName("구독한 적이 없어도 예외 없이 아무 일도 일어나지 않는다")
        void notSubscribed_doesNothingWithoutError() {
            Interest interest = persistInterest("스포츠", "축구");

            subscribeRepository.deleteByInterest_IdAndUserId(interest.getId(), UUID.randomUUID());
        }
    }

    @Nested
    @DisplayName("findSubscriberUsersByInterestIds()")
    class FindSubscriberUsersByInterestIds {

        @Test
        @DisplayName("전달한 관심사들을 구독 중인 사용자만 반환한다")
        void returnsOnlySubscribersOfRequestedInterests() {
            Interest target = persistInterest("스포츠", "축구");
            Interest other = persistInterest("여행", "국내여행");
            User subscriber = persistUser("구독자");
            User otherInterestSubscriber = persistUser("다른관심사구독자");
            persistSubscribe(target, subscriber);
            persistSubscribe(other, otherInterestSubscriber);

            List<InterestSubscriberRow> result =
                    subscribeRepository.findSubscriberUsersByInterestIds(List.of(target.getId()));

            assertThat(result).extracting(row -> row.user().getId()).containsExactly(subscriber.getId());
        }

        @Test
        @DisplayName("논리 삭제된 사용자는 제외한다")
        void excludesDeletedUsers() {
            Interest interest = persistInterest("스포츠", "축구");
            User active = persistUser("활성사용자");
            User deleted = persistDeletedUser("삭제사용자");
            persistSubscribe(interest, active);
            persistSubscribe(interest, deleted);

            List<InterestSubscriberRow> result =
                    subscribeRepository.findSubscriberUsersByInterestIds(List.of(interest.getId()));

            assertThat(result).extracting(row -> row.user().getId()).containsExactly(active.getId());
        }

        @Test
        @DisplayName("구독자가 없으면 빈 목록을 반환한다")
        void noSubscribers_returnsEmptyList() {
            Interest interest = persistInterest("스포츠", "축구");

            List<InterestSubscriberRow> result =
                    subscribeRepository.findSubscriberUsersByInterestIds(List.of(interest.getId()));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("여러 관심사 id를 한 번에 넘기면, 각 결과 행이 어느 관심사의 구독자인지 관심사 id로 구분된다")
        void multipleInterestIds_pairsEachUserWithItsOwnInterestId() {
            Interest sports = persistInterest("스포츠", "축구");
            Interest travel = persistInterest("여행", "국내여행");
            User sportsSubscriber = persistUser("스포츠구독자");
            User travelSubscriber = persistUser("여행구독자");
            persistSubscribe(sports, sportsSubscriber);
            persistSubscribe(travel, travelSubscriber);

            List<InterestSubscriberRow> result = subscribeRepository
                    .findSubscriberUsersByInterestIds(List.of(sports.getId(), travel.getId()));

            assertThat(result)
                    .extracting(InterestSubscriberRow::interestId, row -> row.user().getId())
                    .containsExactlyInAnyOrder(
                            tuple(sports.getId(), sportsSubscriber.getId()),
                            tuple(travel.getId(), travelSubscriber.getId()));
        }
    }

    @Nested
    @DisplayName("findDistinctKeywordsOfSubscribedInterests()")
    class FindDistinctKeywordsOfSubscribedInterests {

        @Test
        @DisplayName("구독 중인 관심사의 키워드만 반환하고, 구독이 없는 관심사의 키워드는 제외한다")
        void returnsOnlyKeywordsOfSubscribedInterests() {
            Interest subscribed = persistInterest("스포츠", "축구", "야구");
            persistInterest("여행", "국내여행");
            persistSubscribe(subscribed, persistUser("구독자"));

            List<String> result = subscribeRepository.findDistinctKeywordsOfSubscribedInterests();

            assertThat(result).containsExactlyInAnyOrder("축구", "야구");
        }

        @Test
        @DisplayName("서로 다른 관심사가 같은 키워드를 가져도 한 번만 반환한다")
        void duplicateKeywordAcrossInterests_returnedOnce() {
            Interest sports = persistInterest("스포츠", "인공지능");
            Interest tech = persistInterest("기술", "인공지능");
            persistSubscribe(sports, persistUser("구독자1"));
            persistSubscribe(tech, persistUser("구독자2"));

            List<String> result = subscribeRepository.findDistinctKeywordsOfSubscribedInterests();

            assertThat(result).containsExactly("인공지능");
        }

        @Test
        @DisplayName("논리 삭제된 사용자의 구독은 제외한다")
        void excludesSubscriptionsOfDeletedUsers() {
            Interest interest = persistInterest("스포츠", "축구");
            persistSubscribe(interest, persistDeletedUser("삭제사용자"));

            List<String> result = subscribeRepository.findDistinctKeywordsOfSubscribedInterests();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("구독이 하나도 없으면 빈 목록을 반환한다")
        void noSubscriptions_returnsEmptyList() {
            persistInterest("스포츠", "축구");

            List<String> result = subscribeRepository.findDistinctKeywordsOfSubscribedInterests();

            assertThat(result).isEmpty();
        }
    }

    @Test
    @DisplayName("countByInterest_Id()는 해당 관심사를 구독 중인 사용자 수만 센다")
    void countByInterest_Id_countsOnlyThatInterestsSubscriptions() {
        Interest target = persistInterest("스포츠", "축구");
        Interest other = persistInterest("여행", "국내여행");

        em.persistAndFlush(Subscribe.of(target, UUID.randomUUID()));
        em.persistAndFlush(Subscribe.of(target, UUID.randomUUID()));
        em.persistAndFlush(Subscribe.of(other, UUID.randomUUID()));

        long count = subscribeRepository.countByInterest_Id(target.getId());

        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("구독 중인 관심사가 없으면 빈 목록을 반환한다")
    void findSubscribedInterestActivities_noSubscriptions_returnsEmptyList() {
        User requester = persistUser("요청자");
        persistInterest("스포츠", "축구");

        List<SubscribedInterestActivityProjection> activities = findActivities(requester.getId());

        assertThat(activities).isEmpty();
    }

    @Test
    @DisplayName("여러 관심사를 구독한 경우 모두 조회된다")
    void findSubscribedInterestActivities_multipleSubscriptions_returnsAll() {
        User requester = persistUser("요청자");
        Interest sports = persistInterest("스포츠", "축구", "야구");
        Interest travel = persistInterest("여행", "항공", "호텔");
        Subscribe sportsSubscribe = persistSubscribe(sports, requester);
        Subscribe travelSubscribe = persistSubscribe(travel, requester);

        List<SubscribedInterestActivityProjection> activities = findActivities(requester.getId());

        assertThat(activities)
                .extracting(SubscribedInterestActivityProjection::id)
                .containsExactlyInAnyOrder(sportsSubscribe.getId(), travelSubscribe.getId());
        assertThat(interestNamesOf(activities)).containsExactlyInAnyOrder("스포츠", "여행");
        assertThat(activities)
                .extracting(SubscribedInterestActivityProjection::interestSubscriberCount)
                .containsOnly(1L);
    }

    @Test
    @DisplayName("구독 관심사가 11건 이상이어도 전체 반환한다")
    void findSubscribedInterestActivities_moreThanTenSubscriptions_returnsAll() {
        User requester = persistUser("요청자");
        List<String> expectedNames = IntStream.rangeClosed(1, 12)
                .mapToObj(i -> "관심사-" + i)
                .toList();

        expectedNames.forEach(name -> {
            Interest interest = persistInterest(name, "키워드-" + name);
            persistSubscribe(interest, requester);
        });

        List<SubscribedInterestActivityProjection> activities = findActivities(requester.getId());

        assertThat(activities).hasSize(12);
        assertThat(interestNamesOf(activities)).containsExactlyInAnyOrderElementsOf(expectedNames);
    }

    @Test
    @DisplayName("구독 행이 없는 관심사는 목록에서 제외된다")
    void findSubscribedInterestActivities_unsubscribedInterestExcluded() {
        User requester = persistUser("요청자");
        Interest subscribed = persistInterest("구독중", "키워드");
        persistInterest("구독안함", "키워드");
        persistSubscribe(subscribed, requester);

        List<SubscribedInterestActivityProjection> activities = findActivities(requester.getId());

        assertThat(interestNamesOf(activities)).containsExactly("구독중");
    }

    @Test
    @DisplayName("논리삭제된 요청 사용자의 구독 활동은 제외된다")
    void findSubscribedInterestActivities_deletedRequester_returnsEmptyList() {
        User requester = persistDeletedUser("삭제사용자");
        Interest interest = persistInterest("스포츠", "축구");
        persistSubscribe(interest, requester);

        List<SubscribedInterestActivityProjection> activities = findActivities(requester.getId());

        assertThat(activities).isEmpty();
    }

    @Test
    @DisplayName("구독자 수는 논리삭제되지 않은 사용자만 집계한다")
    void findSubscribedInterestActivities_countsOnlyActiveSubscribers() {
        User requester = persistUser("요청자");
        User activeUser = persistUser("활성사용자");
        User deletedUser = persistDeletedUser("삭제사용자");
        Interest interest = persistInterest("스포츠", "축구");
        persistSubscribe(interest, requester);
        persistSubscribe(interest, activeUser);
        persistSubscribe(interest, deletedUser);

        List<SubscribedInterestActivityProjection> activities = findActivities(requester.getId());

        assertThat(activities).singleElement()
                .extracting(SubscribedInterestActivityProjection::interestSubscriberCount)
                .isEqualTo(2L);
    }

    @Test
    @DisplayName("구독 생성일 내림차순으로 정렬하고 같은 시각이면 구독 ID 내림차순으로 보조 정렬한다")
    void findSubscribedInterestActivities_ordersBySubscriptionCreatedAtDescAndIdDesc() {
        User requester = persistUser("요청자");
        Subscribe olderSubscribe = persistSubscribe(persistInterest("과거구독", "키워드-과거"), requester);
        Subscribe sameTimeSubscribe1 = persistSubscribe(persistInterest("동시구독-1", "키워드-동시-1"), requester);
        Subscribe sameTimeSubscribe2 = persistSubscribe(persistInterest("동시구독-2", "키워드-동시-2"), requester);
        Subscribe latestSubscribe = persistSubscribe(persistInterest("최신구독", "키워드-최신"), requester);
        em.flush();

        LocalDateTime sameCreatedAt = LocalDateTime.of(2026, 8, 24, 10, 0);
        updateSubscribeCreatedAt(olderSubscribe, sameCreatedAt.minusDays(1));
        updateSubscribeCreatedAt(sameTimeSubscribe1, sameCreatedAt);
        updateSubscribeCreatedAt(sameTimeSubscribe2, sameCreatedAt);
        updateSubscribeCreatedAt(latestSubscribe, sameCreatedAt.plusDays(1));
        em.clear();

        List<UUID> expectedIds = findExpectedSubscribeIdsByNativeOrder(requester.getId());
        List<UUID> resultIds = subscribeRepository.findSubscribedInterestActivities(requester.getId())
                .stream()
                .map(SubscribedInterestActivityProjection::id)
                .toList();

        assertThat(resultIds).containsExactlyElementsOf(expectedIds);
    }

    @Test
    @DisplayName("createdAt은 관심사 생성일이 아니라 구독 생성일이다")
    void findSubscribedInterestActivities_createdAtUsesSubscriptionCreatedAt() {
        User requester = persistUser("요청자");
        Interest interest = persistInterest("스포츠", "축구");
        em.flush();

        Subscribe subscribe = persistSubscribe(interest, requester);
        em.flush();
        LocalDateTime subscriptionCreatedAt = subscribe.getCreatedAt();

        LocalDateTime interestCreatedAt = subscriptionCreatedAt.minusDays(1);
        em.getEntityManager()
                .createQuery("UPDATE Interest i SET i.createdAt = :createdAt WHERE i.id = :id")
                .setParameter("createdAt", interestCreatedAt)
                .setParameter("id", interest.getId())
                .executeUpdate();
        em.flush();
        em.clear();

        List<SubscribedInterestActivityProjection> activities =
                subscribeRepository.findSubscribedInterestActivities(requester.getId());
        SubscribedInterestActivityProjection activity = activities.get(0);

        assertThat(activities).hasSize(1);
        assertThat(activity.createdAt()).isCloseTo(subscriptionCreatedAt, within(1, ChronoUnit.MICROS));
        assertThat(activity.interest().getCreatedAt()).isBefore(activity.createdAt());
    }

    @Test
    @DisplayName("조회 결과를 활동내역 DTO로 변환하면 관심사 현재 상태를 담는다")
    void subscribedInterestActivity_fromProjection_mapsCurrentInterestState() {
        User requester = persistUser("요청자");
        Interest interest = persistInterest("스포츠", "축구", "야구");
        Subscribe subscribe = persistSubscribe(interest, requester);

        List<SubscribedInterestActivityDto> activities = findActivities(requester.getId()).stream()
                .map(SubscribedInterestActivityDto::from)
                .toList();

        assertThat(activities).singleElement()
                .satisfies(activity -> {
                    assertThat(activity.id()).isEqualTo(subscribe.getId());
                    assertThat(activity.createdAt()).isCloseTo(subscribe.getCreatedAt(), within(1, ChronoUnit.MICROS));
                    assertThat(activity.interestId()).isEqualTo(interest.getId());
                    assertThat(activity.interestName()).isEqualTo("스포츠");
                    assertThat(activity.interestKeywords()).containsExactly("축구", "야구");
                    assertThat(activity.interestSubscriberCount()).isEqualTo(1L);
                });
    }

    @Test
    @DisplayName("H2에서는 소량 키워드 배치 로딩이 IN 조건으로 실행된다")
    void subscribedInterestActivity_smallKeywordBatch_h2UsesInCondition(CapturedOutput output) {
        User requester = persistUser("요청자");
        IntStream.rangeClosed(1, 2)
                .mapToObj(i -> persistInterest("소량-" + i, "키워드-" + i))
                .forEach(interest -> persistSubscribe(interest, requester));

        List<SubscribedInterestActivityDto> activities = findActivities(requester.getId()).stream()
                .map(SubscribedInterestActivityDto::from)
                .toList();

        assertThat(activities).hasSize(2);
        assertThat(activities)
                .allSatisfy(activity -> assertThat(activity.interestKeywords()).hasSize(1));
        assertKeywordBatchQueryUsesInCondition(output);
    }

    @Test
    @DisplayName("H2에서는 다량 키워드 배치 로딩도 IN 조건으로 실행된다")
    void subscribedInterestActivity_largeKeywordBatch_h2UsesInCondition(CapturedOutput output) {
        User requester = persistUser("요청자");
        IntStream.rangeClosed(1, 50)
                .mapToObj(i -> persistInterest("다량-" + i, "키워드-" + i))
                .forEach(interest -> persistSubscribe(interest, requester));

        List<SubscribedInterestActivityDto> activities = findActivities(requester.getId()).stream()
                .map(SubscribedInterestActivityDto::from)
                .toList();

        assertThat(activities).hasSize(50);
        assertThat(activities)
                .allSatisfy(activity -> assertThat(activity.interestKeywords()).hasSize(1));
        assertKeywordBatchQueryUsesInCondition(output);
    }

    private User persistUser(String nickname) {
        User user = User.builder()
                .email(UUID.randomUUID() + "@test.com")
                .nickname(nickname)
                .password("password")
                .build();
        em.persist(user);
        return user;
    }

    private User persistDeletedUser(String nickname) {
        User user = persistUser(nickname);
        user.softDelete();
        return user;
    }

    private Interest persistInterest(String name, String... keywords) {
        Interest interest = Interest.create(name);
        for (String keyword : keywords) {
            interest.addKeyword(keyword);
        }
        em.persist(interest);
        return interest;
    }

    private Subscribe persistSubscribe(Interest interest, User user) {
        Subscribe subscribe = Subscribe.of(interest, user.getId());
        em.persist(subscribe);
        return subscribe;
    }

    private void updateSubscribeCreatedAt(Subscribe subscribe, LocalDateTime createdAt) {
        em.getEntityManager()
                .createQuery("UPDATE Subscribe s SET s.createdAt = :createdAt WHERE s.id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", subscribe.getId())
                .executeUpdate();
    }

    private List<SubscribedInterestActivityProjection> findActivities(UUID userId) {
        em.flush();
        em.clear();
        return subscribeRepository.findSubscribedInterestActivities(userId);
    }

    private List<String> interestNamesOf(List<SubscribedInterestActivityProjection> activities) {
        return activities.stream()
                .map(activity -> activity.interest().getName())
                .toList();
    }

    private List<UUID> findExpectedSubscribeIdsByNativeOrder(UUID userId) {
        List<?> rows = em.getEntityManager()
                .createNativeQuery("""
                    SELECT id
                    FROM subscriptions
                    WHERE user_id = :userId
                    ORDER BY created_at DESC, id DESC
                    """)
                .setParameter("userId", userId)
                .getResultList();

        return rows.stream()
                .map(this::toUuid)
                .toList();
    }

    private UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String string) {
            return UUID.fromString(string);
        }
        if (value instanceof byte[] bytes) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            return new UUID(buffer.getLong(), buffer.getLong());
        }
        throw new IllegalArgumentException("지원하지 않는 UUID 타입입니다: " + value.getClass());
    }

    private void assertKeywordBatchQueryUsesInCondition(CapturedOutput output) {
        String sqlLog = output.getOut()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

        assertThat(sqlLog).contains("from keywords");
        assertThat(sqlLog).contains("interest_id in");
        assertThat(sqlLog).doesNotContain("interest_id = any");
        assertThat(sqlLog).doesNotContain("interest_id=any");
    }
}
