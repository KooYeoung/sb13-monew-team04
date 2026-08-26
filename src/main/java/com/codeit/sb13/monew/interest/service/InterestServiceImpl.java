package com.codeit.sb13.monew.interest.service;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.global.dto.CursorPageResponseDto;
import com.codeit.sb13.monew.global.exception.interest.InterestNameDuplicatedException;
import com.codeit.sb13.monew.global.exception.interest.InterestNotFoundException;
import com.codeit.sb13.monew.interest.controller.dto.InterestResponse;
import com.codeit.sb13.monew.interest.domain.Interest;
import com.codeit.sb13.monew.interest.domain.Keyword;
import com.codeit.sb13.monew.interest.repository.InterestRepository;
import com.codeit.sb13.monew.interest.repository.SubscribeRepository;
import com.codeit.sb13.monew.interest.repository.dto.InterestSearchCondition;
import com.codeit.sb13.monew.interest.repository.dto.InterestSearchPage;
import com.codeit.sb13.monew.interest.repository.dto.InterestSubscriberRow;
import com.codeit.sb13.monew.interest.service.dto.InterestCreateCommand;
import com.codeit.sb13.monew.interest.service.dto.InterestOrderBy;
import com.codeit.sb13.monew.interest.service.dto.InterestSearchCommand;
import com.codeit.sb13.monew.interest.service.dto.InterestUpdateCommand;
import com.codeit.sb13.monew.notification.service.NotificationService;
import com.codeit.sb13.monew.notification.service.dto.ArticlesForInterestDto;
import com.codeit.sb13.monew.user.domain.User;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class InterestServiceImpl implements InterestService{

    /**
     * 유사도 임계값의 분자/분모. 0.8을 정수 연산으로 다루기 위해
     * {@code 4/5}로 표현해두고, {@link #SIMILAR_NAME_THRESHOLD}와
     * {@link #minCandidateLength}, {@link #maxCandidateLength}가
     * 이 값을 함께 참조하도록 해 두 계산이 어긋나지 않게 한다.
     */
    private static final int SIMILAR_NAME_THRESHOLD_NUMERATOR = 4;
    private static final int SIMILAR_NAME_THRESHOLD_DENOMINATOR = 5;

    /**
     * 새 이름과 기존 이름의 Levenshtein 유사도가 이 값 이상이면
     * "이미 존재하는 관심사"로 간주해 등록을 거부한다.
     */
    private static final double SIMILAR_NAME_THRESHOLD =
            (double) SIMILAR_NAME_THRESHOLD_NUMERATOR / SIMILAR_NAME_THRESHOLD_DENOMINATOR;

    private final InterestRepository interestRepository;
    private final SubscribeRepository subscribeRepository;
    private final NotificationService notificationService;

    /**
     * {@inheritDoc}
     *
     * <p>이름 중복을 두 단계로 확인한 뒤, {@link Interest#create}로 애그리거트를 만들고
     * 커맨드의 키워드를 순서대로 {@link Interest#addKeyword}에 넘겨 채운다.
     * 새로 만든 관심사는 아직 구독 레코드와 연결된 적이 없으므로, 구독자 수와
     * 구독 여부는 항상 0/false로 응답한다.</p>
     *
     * <p>첫 번째 단계는 {@link InterestRepository#existsByName}으로 완전히 같은
     * 이름이 있는지 확인한다. 인덱스를 타는 가벼운 조회라 대부분의 등록 요청은
     * 이 단계에서 곧바로 끝난다. 두 번째 단계는 {@link #isNameTooSimilarToExisting}로
     * 기존 이름들과 {@link #SIMILAR_NAME_THRESHOLD} 이상 유사한 이름이 있는지 확인한다.
     * 완전히 같은 이름은 유사도가 항상 1.0이라 이 단계에서도 걸러지지만, 후보
     * 이름들을 불러와 하나하나 비교해야 하는 무거운 연산이라 정확 일치를 먼저
     * 걸러낸 뒤에만 실행하도록 순서를 나눴다.</p>
     *
     * <p>사전 중복 확인과 실제 저장 사이에는 경쟁 구간이 존재한다. 두 요청이
     * 동시에 같은 이름으로 등록을 시도하면 둘 다 사전 확인을 통과할 수 있는데,
     * {@code interests.name}에 걸린 유니크 제약이 최후 방어선 역할을 한다.
     * 저장을 {@code saveAndFlush}로 즉시 반영해 제약 위반을 이 메서드 안에서
     * 곧바로 잡아내고, {@link #isNameUniqueViolation}으로 원인이 이름 중복인지
     * 확인해 {@link InterestNameDuplicatedException}으로 변환한다.</p>
     *
     * <p>다만 유사 이름 판별은 DB 제약으로 표현할 수 없는 조건이라, 이 방어선은
     * 정확히 같은 이름에 대해서만 작동하고 서로 다른 두 유사 이름이 동시에
     * 등록되는 경우까지는 막지 못한다. 이걸 완전히 막으려면 등록 흐름 전체를
     * 강하게 직렬화해야 하는데, 그 비용을 극히 드물게 발생하는 경쟁 조건 하나를
     * 막기 위해 모든 등록 요청이 부담하게 되는 셈이라 이번 범위에서는 받아들이지
     * 않기로 했다. 정확히 같은 이름은 DB 제약으로 완전히 보장되고, 유사 이름은
     * 애플리케이션 레벨의 최선 노력(best-effort) 검사로 처리하는 것으로 동시성
     * 보장 수준을 나눈 것이다. advisory lock이나 별도 잠금 테이블로 이 경쟁까지
     * 막는 방법은 있지만, 실제 트래픽 규모와 성능 측정 없이 지금 들이기에는
     * 범위가 크다고 판단해 알려진 한계로 남겨둔다.</p>
     */
    @Override
    public InterestResponse create(InterestCreateCommand command) {
        if (interestRepository.existsByName(command.name())) {
            throw new InterestNameDuplicatedException(command.name());
        }

        if (isNameTooSimilarToExisting(command.name())) {
            throw new InterestNameDuplicatedException(command.name());
        }

        Interest interest = Interest.create(command.name());
        command.keywords().forEach(interest::addKeyword);

        try {
            Interest saved = interestRepository.saveAndFlush(interest);
            return InterestResponse.of(saved, 0L, false);
        } catch (DataIntegrityViolationException e) {
            if (isNameUniqueViolation(e)) {
                throw new InterestNameDuplicatedException(command.name(), e);
            }
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@link Interest#changeKeywords}에 새 키워드 목록을 그대로 넘겨, 달라진
     * 키워드만 반영해 목록을 갱신한다. 이 관심사는 이미 존재하던 관심사라 구독자가
     * 있을 수 있으므로, 생성과 달리 {@link SubscribeRepository#countByInterest_Id}로
     * 실제 구독자 수를 세어 응답에 채운다. 다만 이 요청에는 요청자 정보가
     * 없어 구독 여부까지는 판단할 수 없으므로 항상 false로 응답한다.</p>
     */
    @Override
    public InterestResponse update(InterestUpdateCommand command) {
        Interest interest = interestRepository.findById(command.interestId())
                .orElseThrow(() -> new InterestNotFoundException(command.interestId()));

        interest.changeKeywords(command.keywords());

        long subscriberCount = subscribeRepository.countByInterest_Id(command.interestId());
        return InterestResponse.of(interest, subscriberCount, false);
    }

    /**
     * {@inheritDoc}
     *
     * <p>정렬 기준이 이름이면 {@code interests.name}을, 구독자 수면 구독 테이블에 대한
     * 상관 서브쿼리 결과를 기준으로 정렬한다. 두 경우 모두 생성 시각을 같은 방향의
     * 보조 정렬 기준으로 두어, 정렬 기준 값이 같은 항목들 사이에서도 커서가 안정적으로
     * 다음 페이지를 가리키도록 한다. 실제 쿼리 구성은
     * {@link InterestRepository#search}에 위임한다.</p>
     *
     * <p>목록의 각 항목이 가진 {@code subscriberCount}, {@code subscribedByMe}는
     * {@code InterestRepositoryCustomImpl}이 {@code InterestSearchRow}에 row 단위로
     * 계산해 담아 돌려준 값을 그대로 {@link InterestResponse#of}에 넘겨 조립한다.</p>
     */
    @Override
    public CursorPageResponseDto<InterestResponse> search(InterestSearchCommand command) {
        InterestSearchPage page = interestRepository.search(new InterestSearchCondition(
                command.keyword(),
                command.orderBy(),
                command.direction(),
                command.cursor(),
                command.after(),
                command.idAfter(),
                command.limit(),
                command.requestUserId()
        ));

        List<InterestResponse> content = page.rows().stream()
                .map(row -> InterestResponse.of(row.interest(), row.subscriberCount(), row.subscribedByMe()))
                .toList();

        return new CursorPageResponseDto<>(
                content,
                nextCursor(content, command.orderBy()),
                nextAfter(content),
                nextIdAfter(content),
                content.size(),
                page.totalElements(),
                page.hasNext()
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code Interest}는 {@code keywords}를 {@code cascade = CascadeType.ALL,
     * orphanRemoval = true}로 들고 있어 {@link InterestRepository#delete}만으로
     * 키워드까지 함께 지워진다. 반면 구독은 {@code Subscribe -> Interest} 단방향
     * 연관관계라 cascade 대상이 아니고, {@code subscriptions.interest_id} 외래키에도
     * {@code ON DELETE CASCADE}가 없어 남겨두면 삭제 시점에 제약을 위반한다. 그래서
     * 관심사를 지우기 전에 {@link SubscribeRepository#deleteByInterest_Id}로 구독을
     * 먼저 지운다.</p>
     */
    @Override
    public void delete(UUID interestId) {
        Interest interest = interestRepository.findById(interestId)
                .orElseThrow(() -> new InterestNotFoundException(interestId));

        subscribeRepository.deleteByInterest_Id(interestId);
        interestRepository.delete(interest);
    }

    /**
     * {@inheritDoc}
     *
     * <p>모든 관심사를 순회하며 매칭을 판단한다. 매칭 자체는 DB 쿼리가 아니라
     * 이미 넘겨받은 {@code newArticles}를 대상으로 애플리케이션 메모리에서
     * 비교한다. 새로 수집되는 기사 수와 관심사 수가 이 비교를 감당 못 할
     * 정도로 커지면, 그때는 DB 쪽 매칭으로 옮기는 걸 고려해야 한다.</p>
     *
     * <p>매칭된 관심사가 여러 개여도 구독자 조회는 관심사마다 반복하지 않는다.
     * 먼저 모든 관심사에 대해 매칭 여부와 매칭 건수를 메모리에서 계산해 매칭된
     * 관심사만 추려낸 뒤, 그 관심사 id 전체를 모아
     * {@link SubscribeRepository#findSubscriberUsersByInterestIds}를 한 번만 호출해
     * 알림 수신자를 가져오고 관심사 id별로 묶는다. 매칭되는 관심사 수가 늘어도
     * 구독자 조회 쿼리는 항상 한 번만 실행된다.</p>
     *
     * <p>관심사 목록도 {@link InterestRepository#findAllWithKeywords}로 키워드까지
     * fetch join으로 함께 가져온다. {@code findAll()}만 쓰면 매칭 판단 중
     * {@code interest.getKeywords()}를 호출할 때마다 {@code @BatchSize(size = 100)}
     * 지연 로딩이 걸려, 관심사가 100개를 넘을 때마다 배치 조회가 한 번씩 더 실행된다.</p>
     */
    @Override
    public void notifyForNewArticles(List<Article> newArticles) {
        if (newArticles == null || newArticles.isEmpty()) {
            return;
        }

        List<MatchedInterest> matchedInterests = interestRepository.findAllWithKeywords().stream()
                .map(interest -> new MatchedInterest(interest, countMatchedArticles(interest, newArticles)))
                .filter(matched -> matched.matchedCount() > 0)
                .toList();

        if (matchedInterests.isEmpty()) {
            return;
        }

        List<UUID> matchedInterestIds = matchedInterests.stream()
                .map(matched -> matched.interest().getId())
                .toList();

        Map<UUID, List<User>> recipientsByInterestId = subscribeRepository
                .findSubscriberUsersByInterestIds(matchedInterestIds).stream()
                .collect(Collectors.groupingBy(
                        InterestSubscriberRow::interestId,
                        Collectors.mapping(InterestSubscriberRow::user, Collectors.toList())));

        for (MatchedInterest matched : matchedInterests) {
            Interest interest = matched.interest();
            List<User> recipients = recipientsByInterestId.getOrDefault(interest.getId(), List.of());
            if (recipients.isEmpty()) {
                continue;
            }

            notificationService.notifyArticlesForInterest(new ArticlesForInterestDto(
                    recipients, interest.getId(), interest.getName(), (int) matched.matchedCount()));
        }
    }

    /**
     * {@link #notifyForNewArticles}가 매칭 여부를 먼저 판단하는 단계에서, 관심사와
     * 그 관심사에 매칭된 기사 건수를 함께 들고 다니기 위한 내부 보관용 레코드.
     *
     * @param interest 매칭된 관심사
     * @param matchedCount 그 관심사에 매칭된 기사 건수 (1 이상)
     */
    private record MatchedInterest(Interest interest, long matchedCount) {

    }

    private long countMatchedArticles(Interest interest, List<Article> newArticles) {
        List<String> keywords = interest.getKeywords().stream()
                .map(Keyword::getKeyword)
                .toList();

        return newArticles.stream()
                .filter(article -> matchesAnyKeyword(article, keywords))
                .count();
    }

    private boolean matchesAnyKeyword(Article article, List<String> keywords) {
        return keywords.stream().anyMatch(keyword ->
                containsIgnoreCase(article.getTitle(), keyword)
                        || containsIgnoreCase(article.getSummary(), keyword));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> findSubscribedKeywords() {
        return subscribeRepository.findDistinctKeywordsOfSubscribedInterests();
    }

    /**
     * 두 문자열 중 하나가 다른 하나를 대소문자 구분 없이 포함하는지 확인한다.
     *
     * <p>{@link String#toLowerCase()}를 로케일 없이 쓰면 JVM 기본 로케일이
     * 튀르키예어일 경우 대문자 {@code I}가 점 없는 {@code ı}로 변환되어
     * (예: {@code "AI"}가 {@code "aı"}가 되어) 정상적인 영문 매칭이 실패할 수
     * 있다. 이 문제는 {@link #isNameUniqueViolation}에서도 이미 다룬 것과
     * 같은 문제라, 여기서도 언어와 무관한 {@link Locale#ROOT}를 명시적으로
     * 사용한다.</p>
     */
    private boolean containsIgnoreCase(String text, String keyword) {
        return text != null && keyword != null
                && text.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    /**
     * 다음 페이지 조회 시 {@code cursor} 파라미터로 그대로 돌려보낼 주 커서 값을 만든다.
     *
     * <p>정렬 기준이 이름이면 마지막 항목의 이름을, 구독자 수면 마지막 항목의
     * 구독자 수를 문자열로 담는다. 이번 페이지가 비어 있으면 다음 페이지도 없다는
     * 뜻이라 {@code null}을 돌려준다.</p>
     */
    private String nextCursor(List<InterestResponse> content, InterestOrderBy orderBy) {
        if (content.isEmpty()) {
            return null;
        }

        InterestResponse last = content.get(content.size() - 1);
        return orderBy == InterestOrderBy.NAME
                ? last.name() : String.valueOf(last.subscriberCount());
    }

    /**
     * 다음 페이지 조회 시 {@code after} 파라미터로 그대로 돌려보낼 보조 커서(생성 시각) 값을 만든다.
     */
    private String nextAfter(List<InterestResponse> content) {
        if (content.isEmpty()) {
            return null;
        }

        return content.get(content.size() - 1).createdAt().toString();
    }

    /**
     * 다음 페이지 조회 시 {@code idAfter} 파라미터로 그대로 돌려보낼 3차 커서(마지막 항목의 id) 값을 만든다.
     *
     * <p>{@code cursor}와 {@code after}가 모두 같은 항목이 여러 건 있을 때 순서를 확정하는
     * 타이브레이커로 쓰인다.</p>
     */
    private String nextIdAfter(List<InterestResponse> content) {
        if (content.isEmpty()) {
            return null;
        }

        return content.get(content.size() - 1).id().toString();
    }

    /**
     * 저장 시점에 발생한 무결성 위반이 {@code interests.name}의 유니크 제약
     * ({@code uk_interests_name}) 때문인지 판별한다.
     *
     * <p>운영 DB(Postgres)는 따옴표로 감싸지 않은 식별자를 소문자로 저장하지만,
     * 테스트에 쓰는 H2는 제약/인덱스 이름을 대문자로 돌려준다
     * (예: {@code "PUBLIC.UK_INTERESTS_NAME INDEX PUBLIC.UK_INTERESTS_NAME_INDEX_C ..."}).
     * DB마다 대소문자 표기가 달라질 수 있으므로, 메시지를 소문자로 바꾼 뒤
     * 비교해 대소문자에 관계없이 판별되도록 한다. 이때 {@link String#toLowerCase()}를
     * 로케일 없이 쓰면 JVM 기본 로케일이 튀르키예어일 경우 대문자 {@code I}가
     * 점 없는 {@code ı}로 변환되어 {@code "INTERESTS"}가 {@code "interests"}가 아닌
     * {@code "ınterests"}로 바뀌는 문제가 있어, 언어와 무관한 {@link Locale#ROOT}를
     * 명시적으로 사용한다.</p>
     */
    private boolean isNameUniqueViolation(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null
                && message.toLowerCase(Locale.ROOT).contains("uk_interests_name");
    }

    /**
     * 새로 등록하려는 이름이 기존 관심사 이름 중 하나와
     * {@link #SIMILAR_NAME_THRESHOLD} 이상 유사한지 확인한다.
     *
     * <p>기존 이름 전체를 불러와 비교하지 않고, 길이만으로 80% 유사도에
     * 도달할 수 없는 이름을 먼저 걸러낸다. Levenshtein 편집 거리는 두 문자열의
     * 길이 차이보다 작을 수 없으므로, 새 이름 길이가 {@code L}일 때 후보 이름의
     * 길이는 {@code [ceil(0.8L), floor(L/0.8)]} 범위를 벗어나면 유사도가 절대
     * 0.8 이상이 될 수 없다. 이 범위는 {@link #minCandidateLength}와
     * {@link #maxCandidateLength}로 계산해
     * {@link InterestRepository#findNamesByLengthBetween}에 넘긴다.</p>
     *
     * @param newName 새로 등록하려는 관심사 이름
     * @return 후보 이름 중 하나라도 임계값 이상 유사하면 {@code true}
     */
    private boolean isNameTooSimilarToExisting(String newName) {
        int length = newName.length();
        int minLength = minCandidateLength(length);
        int maxLength = maxCandidateLength(length);

        return interestRepository.findNamesByLengthBetween(minLength, maxLength).stream()
                .anyMatch(existingName ->
                        getLevenshteinMatchRate(newName, existingName) >= SIMILAR_NAME_THRESHOLD);
    }

    /**
     * 유사도 {@link #SIMILAR_NAME_THRESHOLD} 이상이 되려면 후보 이름의 길이가
     * 최소 얼마여야 하는지 계산한다.
     *
     * <p>{@code ceil(SIMILAR_NAME_THRESHOLD * length)}를 부동소수점 없이
     * 정수 연산만으로 구한다. {@code ceil(a/b) = (a + b - 1) / b} 공식에
     * {@code a = NUMERATOR * length}, {@code b = DENOMINATOR}를 대입한 것이다.</p>
     */
    private int minCandidateLength(int length) {
        int numerator = SIMILAR_NAME_THRESHOLD_NUMERATOR * length;
        return (numerator + SIMILAR_NAME_THRESHOLD_NUMERATOR) / SIMILAR_NAME_THRESHOLD_DENOMINATOR;
    }

    /**
     * 유사도 {@link #SIMILAR_NAME_THRESHOLD} 이상이 되려면 후보 이름의 길이가
     * 최대 얼마까지 가능한지 계산한다.
     *
     * <p>{@code floor(length / SIMILAR_NAME_THRESHOLD)}를 부동소수점 없이
     * 정수 연산만으로 구한다({@code length * DENOMINATOR / NUMERATOR}).</p>
     */
    private int maxCandidateLength(int length) {
        return (length * SIMILAR_NAME_THRESHOLD_DENOMINATOR) / SIMILAR_NAME_THRESHOLD_NUMERATOR;
    }

    /**
     * 두 문자열의 Levenshtein 편집 거리를 0(전혀 다름)~1(완전히 같음) 사이의
     * 유사도로 환산한다.
     *
     * <p>{@code 1 - (편집 거리 / 두 문자열 중 더 긴 쪽의 길이)}로 계산한다.
     * 예를 들어 길이 6인 문자열이 길이 5인 문자열과 편집 거리 1(글자 하나 차이)이면
     * 유사도는 {@code 1 - 1/6 ≈ 0.833}이다. 두 문자열이 모두 빈 문자열이면
     * 편집 거리도 0이고 나눌 길이도 0이 되어 0으로 나누기 문제가 생기므로,
     * 이 경우는 완전히 같은 것으로 보아 1.0을 반환한다.</p>
     *
     * @param newName      새로 등록하려는 관심사 이름
     * @param existingName 비교 대상이 되는 기존 관심사 이름
     * @return 0.0(전혀 다름) ~ 1.0(완전히 같음) 사이의 유사도
     */
    private double getLevenshteinMatchRate(String newName, String existingName) {
        int maxLength = Math.max(newName.length(), existingName.length());
        if (maxLength == 0) return 1.0;

        int distance = LevenshteinDistance.getDefaultInstance().apply(newName, existingName);
        return 1.0 - (double) distance / maxLength;
    }
}
