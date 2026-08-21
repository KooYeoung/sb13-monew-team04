package com.codeit.sb13.monew.interest.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.codeit.sb13.monew.global.config.JpaAuditingConfig;
import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import com.codeit.sb13.monew.global.exception.interest.InterestSearchConditionInvalidException;
import com.codeit.sb13.monew.global.config.QueryDslConfig;
import com.codeit.sb13.monew.interest.domain.Interest;
import com.codeit.sb13.monew.interest.domain.Subscribe;
import com.codeit.sb13.monew.interest.repository.dto.InterestSearchCondition;
import com.codeit.sb13.monew.interest.repository.dto.InterestSearchPage;
import com.codeit.sb13.monew.interest.repository.dto.InterestSearchRow;
import com.codeit.sb13.monew.interest.service.dto.InterestOrderBy;
import com.codeit.sb13.monew.user.domain.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@link InterestRepositoryCustomImpl#search}에 대한 통합 테스트.
 *
 * <p>{@code @DataJpaTest}는 {@link QueryDslConfig}나 {@link JpaAuditingConfig}처럼
 * 리포지토리/엔티티가 아닌 일반 {@code @Configuration}은 자동으로 스캔하지 않는다.
 * {@code JPAQueryFactory}를 쓰는 커스텀 구현을 테스트하려면 {@link QueryDslConfig}가,
 * {@code @CreatedDate}로 채워지는 {@code createdAt}(커서의 보조 기준으로 쓰인다)이
 * 실제로 채워지려면 {@link JpaAuditingConfig}가 각각 필요해 둘 다 명시적으로
 * {@code @Import}한다.</p>
 */
@DataJpaTest
@Import({QueryDslConfig.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
class InterestRepositoryCustomImplTest {

    @Autowired
    private InterestRepository interestRepository;

    @Autowired
    private TestEntityManager em;

    private UUID persistUser() {
        User user = User.builder()
                .email(UUID.randomUUID() + "@test.com")
                .nickname("테스터")
                .password("password")
                .build();
        em.persist(user);
        return user.getId();
    }

    private Interest persistInterest(String name, String... keywords) {
        Interest interest = Interest.create(name);
        for (String keyword : keywords) {
            interest.addKeyword(keyword);
        }
        em.persist(interest);
        return interest;
    }

    private void subscribe(Interest interest, UUID userId) {
        em.persist(Subscribe.of(interest, userId));
    }

    /**
     * {@link InterestSearchPage#rows}에서 {@link Interest} 목록만 뽑아낸다.
     *
     * <p>{@code InterestSearchPage}는 {@code InterestSearchRow} 목록만 담고 있어,
     * 정렬 순서나 이름을 확인하는 테스트에서 매번 {@code .rows().stream().map(...)}을
     * 반복하지 않도록 뽑아낸 헬퍼다.</p>
     */
    private List<Interest> interestsOf(InterestSearchPage page) {
        return page.rows().stream().map(InterestSearchRow::interest).toList();
    }

    /**
     * {@link InterestSearchPage#rows}를 관심사 id별 구독자 수 맵으로 다시 조립한다.
     * 리포지토리가 실제로 이 맵을 만들어 돌려주는 것은 아니지만, 테스트에서 특정
     * 관심사의 구독자 수를 id로 바로 찾아보기 편하도록 이 헬퍼에서만 조립한다.
     */
    private Map<UUID, Long> subscriberCountsOf(InterestSearchPage page) {
        return page.rows().stream()
                .collect(Collectors.toMap(row -> row.interest().getId(), InterestSearchRow::subscriberCount));
    }

    /**
     * {@link InterestSearchPage#rows} 중 요청자가 구독 중인 관심사의 id만 집합으로 뽑아낸다.
     */
    private Set<UUID> subscribedInterestIdsOf(InterestSearchPage page) {
        return page.rows().stream()
                .filter(InterestSearchRow::subscribedByMe)
                .map(row -> row.interest().getId())
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("정렬 기준이 이름이면 오름차순으로 정렬해 반환한다")
    void search_orderByNameAscending_returnsSortedByName() {
        persistInterest("다람쥐", "동물");
        persistInterest("가나다", "동물");
        persistInterest("나비", "동물");
        em.flush();
        em.clear();

        InterestSearchPage page = interestRepository.search(new InterestSearchCondition(
                null, InterestOrderBy.NAME, Sort.Direction.ASC, null, null, null, 10, null));

        assertThat(interestsOf(page)).extracting(Interest::getName)
                .containsExactly("가나다", "나비", "다람쥐");
        assertThat(page.hasNext()).isFalse();
        assertThat(page.totalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("검색어는 관심사 이름과 키워드 텍스트 모두에서 매칭된다")
    void search_keywordFilter_matchesNameOrKeywordText() {
        persistInterest("스포츠", "축구");
        persistInterest("여행", "스포츠용품");
        persistInterest("요리", "레시피");
        em.flush();
        em.clear();

        InterestSearchPage page = interestRepository.search(new InterestSearchCondition(
                "스포츠", InterestOrderBy.NAME, Sort.Direction.ASC, null, null, null, 10, null));

        assertThat(interestsOf(page)).extracting(Interest::getName)
                .containsExactlyInAnyOrder("스포츠", "여행");
        assertThat(page.totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("구독자 수와 요청자의 구독 여부를 정확히 계산한다")
    void search_computesSubscriberCountAndSubscribedByMe() {
        UUID requester = persistUser();
        UUID other = persistUser();

        Interest popular = persistInterest("인기관심사", "키워드");
        Interest lonely = persistInterest("비인기관심사", "키워드");
        em.flush();

        subscribe(popular, requester);
        subscribe(popular, other);
        em.flush();
        em.clear();

        InterestSearchPage page = interestRepository.search(new InterestSearchCondition(
                null, InterestOrderBy.NAME, Sort.Direction.ASC, null, null, null, 10, requester));

        assertThat(subscriberCountsOf(page).get(popular.getId())).isEqualTo(2L);
        assertThat(subscriberCountsOf(page).getOrDefault(lonely.getId(), 0L)).isEqualTo(0L);
        assertThat(subscribedInterestIdsOf(page)).contains(popular.getId());
        assertThat(subscribedInterestIdsOf(page)).doesNotContain(lonely.getId());
    }

    @Test
    @DisplayName("구독자 수 기준 내림차순 정렬이 정확하다")
    void search_orderBySubscriberCountDescending_returnsSortedBySubscriberCount() {
        UUID userA = persistUser();
        UUID userB = persistUser();

        Interest twoSubscribers = persistInterest("A", "키워드");
        Interest oneSubscriber = persistInterest("B", "키워드");
        persistInterest("C", "키워드");
        em.flush();

        subscribe(twoSubscribers, userA);
        subscribe(twoSubscribers, userB);
        subscribe(oneSubscriber, userA);
        em.flush();
        em.clear();

        InterestSearchPage page = interestRepository.search(new InterestSearchCondition(
                null, InterestOrderBy.SUBSCRIBER_COUNT, Sort.Direction.DESC, null, null, null, 10, null));

        assertThat(interestsOf(page)).extracting(Interest::getName)
                .containsExactly("A", "B", "C");
    }

    @Test
    @DisplayName("커서와 보조 커서를 넘기면 이전 페이지 다음부터 이어서 조회한다")
    void search_withCursorAndAfter_continuesFromPreviousPage() {
        persistInterest("가", "키워드");
        persistInterest("나", "키워드");
        persistInterest("다", "키워드");
        persistInterest("라", "키워드");
        persistInterest("마", "키워드");
        em.flush();
        em.clear();

        InterestSearchPage firstPage = interestRepository.search(new InterestSearchCondition(
                null, InterestOrderBy.NAME, Sort.Direction.ASC, null, null, null, 2, null));

        assertThat(interestsOf(firstPage)).extracting(Interest::getName)
                .containsExactly("가", "나");
        assertThat(firstPage.hasNext()).isTrue();

        Interest lastOfFirstPage = interestsOf(firstPage).get(interestsOf(firstPage).size() - 1);
        String cursor = lastOfFirstPage.getName();
        LocalDateTime after = lastOfFirstPage.getCreatedAt();
        UUID idAfter = lastOfFirstPage.getId();

        InterestSearchPage secondPage = interestRepository.search(new InterestSearchCondition(
                null, InterestOrderBy.NAME, Sort.Direction.ASC, cursor, after, idAfter, 2, null));

        assertThat(interestsOf(secondPage)).extracting(Interest::getName)
                .containsExactly("다", "라");
        assertThat(secondPage.hasNext()).isTrue();

        InterestSearchPage thirdPage = interestRepository.search(new InterestSearchCondition(
                null, InterestOrderBy.NAME, Sort.Direction.ASC,
                interestsOf(secondPage).get(1).getName(), interestsOf(secondPage).get(1).getCreatedAt(),
                interestsOf(secondPage).get(1).getId(), 2, null));

        assertThat(interestsOf(thirdPage)).extracting(Interest::getName)
                .containsExactly("마");
        assertThat(thirdPage.hasNext()).isFalse();
    }

    @Test
    @DisplayName("페이지 안에 담긴 각 관심사의 키워드도 함께 채워진다")
    void search_populatesKeywordsForEachInterest() {
        persistInterest("스포츠", "축구", "야구");
        em.flush();
        em.clear();

        InterestSearchPage page = interestRepository.search(new InterestSearchCondition(
                null, InterestOrderBy.NAME, Sort.Direction.ASC, null, null, null, 10, null));

        assertThat(interestsOf(page)).hasSize(1);
        assertThat(interestsOf(page).get(0).getKeywords())
                .extracting(k -> k.getKeyword())
                .containsExactlyInAnyOrder("축구", "야구");
    }

    @Test
    @DisplayName("이름 내림차순 정렬에서도 커서를 넘기면 다음 페이지로 이어진다")
    void search_orderByNameDescending_withCursor_continuesFromPreviousPage() {
        persistInterest("가", "키워드");
        persistInterest("나", "키워드");
        persistInterest("다", "키워드");
        em.flush();
        em.clear();

        InterestSearchPage firstPage = interestRepository.search(new InterestSearchCondition(
                null, InterestOrderBy.NAME, Sort.Direction.DESC, null, null, null, 2, null));

        assertThat(interestsOf(firstPage)).extracting(Interest::getName)
                .containsExactly("다", "나");
        assertThat(firstPage.hasNext()).isTrue();

        Interest lastOfFirstPage = interestsOf(firstPage).get(interestsOf(firstPage).size() - 1);

        InterestSearchPage secondPage = interestRepository.search(new InterestSearchCondition(
                null, InterestOrderBy.NAME, Sort.Direction.DESC,
                lastOfFirstPage.getName(), lastOfFirstPage.getCreatedAt(), lastOfFirstPage.getId(), 2, null));

        assertThat(interestsOf(secondPage)).extracting(Interest::getName)
                .containsExactly("가");
        assertThat(secondPage.hasNext()).isFalse();
    }

    @Test
    @DisplayName("구독자 수 오름차순 정렬에서 커서를 넘기면 다음 페이지로 이어진다")
    void search_orderBySubscriberCountAscending_withCursor_continuesFromPreviousPage() {
        UUID userA = persistUser();
        UUID userB = persistUser();

        persistInterest("A", "키워드");
        Interest oneSubscriber = persistInterest("B", "키워드");
        Interest twoSubscribers = persistInterest("C", "키워드");
        em.flush();

        subscribe(oneSubscriber, userA);
        subscribe(twoSubscribers, userA);
        subscribe(twoSubscribers, userB);
        em.flush();
        em.clear();

        InterestSearchPage firstPage = interestRepository.search(new InterestSearchCondition(
                null, InterestOrderBy.SUBSCRIBER_COUNT, Sort.Direction.ASC, null, null, null, 2, null));

        assertThat(interestsOf(firstPage)).extracting(Interest::getName)
                .containsExactly("A", "B");
        assertThat(firstPage.hasNext()).isTrue();

        Interest lastOfFirstPage = interestsOf(firstPage).get(interestsOf(firstPage).size() - 1);
        String cursor = String.valueOf(subscriberCountsOf(firstPage).getOrDefault(lastOfFirstPage.getId(), 0L));

        InterestSearchPage secondPage = interestRepository.search(new InterestSearchCondition(
                null, InterestOrderBy.SUBSCRIBER_COUNT, Sort.Direction.ASC,
                cursor, lastOfFirstPage.getCreatedAt(), lastOfFirstPage.getId(), 2, null));

        assertThat(interestsOf(secondPage)).extracting(Interest::getName)
                .containsExactly("C");
        assertThat(secondPage.hasNext()).isFalse();
    }

    @Test
    @DisplayName("구독자 수 내림차순 정렬에서 커서를 넘기면 다음 페이지로 이어진다")
    void search_orderBySubscriberCountDescending_withCursor_continuesFromPreviousPage() {
        UUID userA = persistUser();
        UUID userB = persistUser();

        persistInterest("A", "키워드");
        Interest oneSubscriber = persistInterest("B", "키워드");
        Interest twoSubscribers = persistInterest("C", "키워드");
        em.flush();

        subscribe(oneSubscriber, userA);
        subscribe(twoSubscribers, userA);
        subscribe(twoSubscribers, userB);
        em.flush();
        em.clear();

        InterestSearchPage firstPage = interestRepository.search(new InterestSearchCondition(
                null, InterestOrderBy.SUBSCRIBER_COUNT, Sort.Direction.DESC, null, null, null, 2, null));

        assertThat(interestsOf(firstPage)).extracting(Interest::getName)
                .containsExactly("C", "B");
        assertThat(firstPage.hasNext()).isTrue();

        Interest lastOfFirstPage = interestsOf(firstPage).get(interestsOf(firstPage).size() - 1);
        String cursor = String.valueOf(subscriberCountsOf(firstPage).getOrDefault(lastOfFirstPage.getId(), 0L));

        InterestSearchPage secondPage = interestRepository.search(new InterestSearchCondition(
                null, InterestOrderBy.SUBSCRIBER_COUNT, Sort.Direction.DESC,
                cursor, lastOfFirstPage.getCreatedAt(), lastOfFirstPage.getId(), 2, null));

        assertThat(interestsOf(secondPage)).extracting(Interest::getName)
                .containsExactly("A");
        assertThat(secondPage.hasNext()).isFalse();
    }

    @Test
    @DisplayName("구독자 수 기준 커서 값이 숫자가 아니면 InterestSearchConditionInvalidException을 던진다")
    void search_invalidSubscriberCountCursor_throwsException() {
        persistInterest("스포츠", "키워드");
        em.flush();
        em.clear();

        // IllegalArgumentException 대신 전용 예외를 쓰는 이유는 InterestSearchConditionInvalidException의
        // javadoc 참고. 이 타입은 Spring이 인식하는 JPA 예외가 아니므로, 리포지토리 프록시를
        // 거쳐도 InvalidDataAccessApiUsageException으로 감싸지지 않고 그대로 전달된다.
        // MonewException의 메시지는 ApiErrorCode의 고정 메시지라, 실제 원인은 details에서 확인한다.
        InterestSearchConditionInvalidException e = catchThrowableOfType(
                () -> interestRepository.search(new InterestSearchCondition(
                        null, InterestOrderBy.SUBSCRIBER_COUNT, Sort.Direction.DESC,
                        "숫자아님", LocalDateTime.now(), UUID.randomUUID(), 10, null)),
                InterestSearchConditionInvalidException.class);

        assertThat(e.getApiErrorCode()).isEqualTo(ApiErrorCode.INTEREST_SEARCH_CONDITION_INVALID);
        assertThat(e.getDetails()).containsEntry("reason", "구독자 수 기준 커서 값이 올바르지 않습니다: 숫자아님");
    }

    @Test
    @DisplayName("검색 결과가 없으면 빈 목록과 함께 구독 정보 조회도 건너뛴다")
    void search_noResults_returnsEmptyPage() {
        UUID requester = persistUser();
        persistInterest("스포츠", "축구");
        em.flush();
        em.clear();

        InterestSearchPage page = interestRepository.search(new InterestSearchCondition(
                "존재하지않는검색어", InterestOrderBy.NAME, Sort.Direction.ASC, null, null, null, 10, requester));

        assertThat(interestsOf(page)).isEmpty();
        assertThat(page.hasNext()).isFalse();
        assertThat(page.totalElements()).isEqualTo(0);
        assertThat(subscribedInterestIdsOf(page)).isEmpty();
        assertThat(subscriberCountsOf(page)).isEmpty();
    }

    @Test
    @DisplayName("정렬 기준과 생성 시각까지 같아도 id로 순서를 확정해 페이지 경계에서 누락·중복이 없다")
    void search_tiedPrimaryAndCreatedAt_usesIdAsFinalTiebreaker() {
        // 셋 다 구독자 0명이라 정렬 기준(구독자 수)이 이미 동률이고,
        // 아래에서 createdAt까지 강제로 동일하게 맞춰 id가 최종 순서를 결정하도록 만든다.
        Interest a = persistInterest("A", "키워드");
        Interest b = persistInterest("B", "키워드");
        Interest c = persistInterest("C", "키워드");
        em.flush();

        LocalDateTime sameInstant = LocalDateTime.now();
        em.getEntityManager()
                .createQuery("UPDATE Interest i SET i.createdAt = :createdAt WHERE i.id IN :ids")
                .setParameter("createdAt", sameInstant)
                .setParameter("ids", List.of(a.getId(), b.getId(), c.getId()))
                .executeUpdate();
        em.clear();

        InterestSearchPage firstPage = interestRepository.search(new InterestSearchCondition(
                null, InterestOrderBy.SUBSCRIBER_COUNT, Sort.Direction.ASC, null, null, null, 2, null));

        assertThat(interestsOf(firstPage)).hasSize(2);
        assertThat(firstPage.hasNext()).isTrue();

        Interest lastOfFirstPage = interestsOf(firstPage).get(interestsOf(firstPage).size() - 1);
        String cursor = String.valueOf(
                subscriberCountsOf(firstPage).getOrDefault(lastOfFirstPage.getId(), 0L));

        InterestSearchPage secondPage = interestRepository.search(new InterestSearchCondition(
                null, InterestOrderBy.SUBSCRIBER_COUNT, Sort.Direction.ASC,
                cursor, lastOfFirstPage.getCreatedAt(), lastOfFirstPage.getId(), 2, null));

        assertThat(interestsOf(secondPage)).hasSize(1);
        assertThat(secondPage.hasNext()).isFalse();

        // 페이지 경계에서 id 타이브레이커가 없었다면 createdAt 동률로 인해
        // 같은 행이 양쪽 페이지에 중복되거나, 반대로 어느 쪽에도 나타나지 않을 수 있다.
        List<UUID> combinedIds = List.of(
                interestsOf(firstPage).get(0).getId(),
                interestsOf(firstPage).get(1).getId(),
                interestsOf(secondPage).get(0).getId()
        );
        assertThat(combinedIds).containsExactlyInAnyOrder(a.getId(), b.getId(), c.getId());
    }
}
