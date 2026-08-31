package com.codeit.sb13.monew.interest.repository;

import static com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus.ACTIVE;
import static com.codeit.sb13.monew.interest.domain.QInterest.interest;
import static com.codeit.sb13.monew.interest.domain.QSubscribe.subscribe;

import com.codeit.sb13.monew.global.exception.interest.InterestSearchConditionInvalidException;
import com.codeit.sb13.monew.interest.domain.QKeyword;
import com.codeit.sb13.monew.interest.repository.dto.InterestSearchCondition;
import com.codeit.sb13.monew.interest.repository.dto.InterestSearchPage;
import com.codeit.sb13.monew.interest.repository.dto.InterestSearchRow;
import com.codeit.sb13.monew.interest.service.dto.InterestOrderBy;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

/**
 * {@link InterestRepositoryCustom}의 QueryDSL 구현.
 *
 * <p>구독자 수는 별도 컬럼이 아니라 {@code subscriptions}에 대한 상관 서브쿼리로 계산한다.
 * 키워드 매칭도 {@code keywords} 테이블에 대한 EXISTS 서브쿼리로 처리하는데, 두 방식 모두
 * JOIN 대신 서브쿼리를 쓰는 이유는 같다. {@code interests}가 {@code keywords}(1:N)나
 * {@code subscriptions}(1:N)와 조인되면 관심사 하나가 여러 행으로 뻥튀기(fan-out)되어,
 * 페이지네이션의 LIMIT이 관심사 개수가 아니라 조인된 행 개수를 자르게 되고 구독자 수
 * 집계도 흐트러진다. 서브쿼리는 관심사 1건당 정확히 1행을 유지하면서 값만 끌어오므로
 * 이 문제가 생기지 않는다.</p>
 *
 * <p>구독자 수와 요청자의 구독 여부는 {@code Subscribe.visibilityStatus = ACTIVE}인
 * 구독만 대상으로 계산한다. 사용자 삭제 시 구독의 노출 상태가 함께 갱신되므로,
 * 조회마다 {@code users}를 조인해 {@code deletedAt}을 다시 확인하지 않는다.</p>
 */
@RequiredArgsConstructor
public class InterestRepositoryCustomImpl implements InterestRepositoryCustom {

    /**
     * {@code Keyword} 엔티티의 속성 이름이 하필 {@code keyword}라서, QueryDSL이 관례로
     * 만들어주는 기본 인스턴스({@code QKeyword.keyword})는 같은 이름의 필드와 충돌해
     * 생성되지 않는다. 그래서 별칭을 직접 지정해 인스턴스를 만든다.
     */
    private static final QKeyword keyword = new QKeyword("keyword");

    private final JPAQueryFactory queryFactory;

    @Override
    public InterestSearchPage search(InterestSearchCondition condition) {
        String keywordText = condition.keyword();
        InterestOrderBy orderBy = condition.orderBy();
        Sort.Direction direction = condition.direction();
        UUID cursor = condition.cursor();
        LocalDateTime after = condition.after();
        int limit = condition.limit();
        UUID requestUserId = condition.requestUserId();

        NumberExpression<Long> subscriberCountExpr = subscriberCountExpression();
        BooleanExpression subscribedByMeExpr = subscribedByMeExpression(requestUserId);
        BooleanExpression searchCondition = searchCondition(keywordText);

        // cursor(이전 페이지 마지막 항목의 id)로 그 행을 다시 조회해, 키셋 비교에 쓸
        // "현재" 이름/구독자 수를 가져온다. 클라이언트가 들고 있던 값을 그대로 신뢰하지 않는
        // 이유는 resolveAnchor의 문서를 참고.
        AnchorRow anchor = resolveAnchor(cursor, after, subscriberCountExpr);
        BooleanExpression keysetCondition =
                keysetCondition(orderBy, direction, anchor, after, cursor, subscriberCountExpr);

        // 다음 페이지 존재 여부를 별도 쿼리 없이 판단하기 위해 요청한 limit보다 하나 더 가져온다.
        List<InterestSearchRow> rows = queryFactory
                .select(Projections.constructor(
                        // subscriberCountExpr는 서브쿼리를 감싼 표현식이라 getType()이 Long이 아니라
                        // Object로 소실되는 QueryDSL의 알려진 특성이 있다. Projections.constructor는
                        // 각 표현식의 getType()을 리플렉션으로 읽어 생성자 파라미터 타입과 맞춰보므로,
                        // longValue()로 타입을 Long으로 다시 명시해줘야 InterestSearchRow의 생성자를 찾는다.
                        InterestSearchRow.class, interest, subscriberCountExpr.longValue(), subscribedByMeExpr))
                .from(interest)
                .where(searchCondition, keysetCondition)
                .orderBy(orderSpecifiers(orderBy, direction, subscriberCountExpr))
                .limit(limit + 1L)
                .fetch();

        boolean hasNext = rows.size() > limit;
        List<InterestSearchRow> pageRows = hasNext ? rows.subList(0, limit) : rows;

        // 키워드는 여기서 즉시 조회하지 않는다. Interest.keywords에 붙은 @BatchSize(size = 100) 덕분에,
        // 이후 같은 트랜잭션 안에서 interest.getKeywords()가 처음 호출되는 시점에 이 페이지에 담긴
        // 관심사들의 id를 묶어 한 번의 지연 로딩 쿼리로 조회된다(InterestServiceImpl#search 참고).

        Long totalElements = queryFactory
                .select(interest.count())
                .from(interest)
                .where(searchCondition)
                .fetchOne();

        return new InterestSearchPage(
                pageRows,
                hasNext,
                totalElements == null ? 0L : totalElements
        );
    }

    /**
     * {@code cursor}(이전 페이지 마지막 관심사의 id)로 그 행을 다시 조회해, 키셋 비교에 쓸
     * "현재" 이름과 구독자 수를 가져온다.
     *
     * <p>{@code cursor} 문자열을 그대로 파싱해 비교 기준값으로 쓰던 이전 방식은, 그 값이
     * 이전 페이지 응답을 만든 시점의 스냅샷이라는 문제가 있었다. 그 사이 해당 관심사의
     * 구독자 수가 바뀌면(누군가 구독/구독 해지) 클라이언트가 들고 있는 값과 실제 값이
     * 어긋나, 페이지 경계에서 항목이 누락되거나 중복될 수 있다. 여기서는 대신 {@code cursor}가
     * 가리키는 행을 매 요청마다 다시 조회해, 그 시점 기준 정확한 값으로 비교한다.</p>
     *
     * <p>{@code cursor}와 {@code after}가 둘 다 없으면 첫 페이지 조회로 보고 조회 자체를
     * 생략한다. 둘 중 하나만 있으면 클라이언트가 커서를 일부만 보낸 잘못된 요청이므로,
     * 이를 첫 페이지 조회로 처리해 조용히 첫 페이지를 다시 돌려주는 대신(클라이언트 입장에서는
     * 이전 페이지 항목이 중복으로 보인다) 예외를 던져 요청 자체가 잘못됐음을 알린다.
     * {@code cursor}로 조회했는데 행이 없으면 그 사이 관심사가 삭제된 것이므로, 더 이상
     * 정확한 이어보기 위치를 계산할 수 없다는 뜻으로 예외를 던진다.</p>
     */
    private AnchorRow resolveAnchor(UUID cursor, LocalDateTime after, NumberExpression<Long> subscriberCountExpr) {
        if (cursor == null && after == null) {
            return null;
        }

        if (cursor == null || after == null) {
            throw new InterestSearchConditionInvalidException("cursor와 after는 함께 전달해야 합니다.");
        }

        // Projections.constructor로 AnchorRow에 바로 매핑하는 대신, 조회 결과를 Tuple로
        // 받아 값을 직접 꺼내 생성자를 호출한다. 리플렉션 기반 생성자 매칭을 거치지 않아
        // 더 단순하고, 위 InterestSearchRow 매핑과 달리 타입 소실 문제에서도 자유롭다.
        NumberExpression<Long> countExpr = subscriberCountExpr.longValue();
        Tuple anchorTuple = queryFactory
                .select(interest.name, countExpr)
                .from(interest)
                .where(interest.id.eq(cursor))
                .fetchOne();

        if (anchorTuple == null) {
            throw new InterestSearchConditionInvalidException("커서가 가리키는 관심사를 더 이상 찾을 수 없습니다: " + cursor);
        }

        return new AnchorRow(anchorTuple.get(interest.name), anchorTuple.get(countExpr));
    }

    /**
     * {@link #resolveAnchor}가 다시 조회한 앵커 행의 값을 담는 보관용 클래스.
     *
     * <p>{@code name}은 앵커 관심사의 현재 이름, {@code subscriberCount}는 앵커 관심사의
     * 현재 활성 구독자 수이다. {@link #resolveAnchor}에서
     * {@code Tuple} 조회 결과로 채워 넣는다.</p>
     */
    private static final class AnchorRow {

        private final String name;
        private final Long subscriberCount;

        private AnchorRow(String name, Long subscriberCount) {
            this.name = name;
            this.subscriberCount = subscriberCount;
        }

        private String name() {
            return name;
        }

        private Long subscriberCount() {
            return subscriberCount;
        }
    }

    private NumberExpression<Long> subscriberCountExpression() {
        return Expressions.asNumber(
                JPAExpressions.select(subscribe.count())
                        .from(subscribe)
                        .where(
                                subscribe.interest.eq(interest),
                                subscribe.visibilityStatus.eq(ACTIVE)
                        )
        );
    }

    /**
     * 요청자가 이 관심사를 활성 상태로 구독 중인지 여부를 서브쿼리로 계산한다.
     *
     * <p>{@code requestUserId}가 {@code null}이면(비로그인 요청) 구독 여부를 물을 대상이
     * 없으므로 서브쿼리 없이 상수 {@code false}를 돌려준다. {@code subscribe.userId.eq(null)}을
     * 직접 조건에 넣으면 SQL의 {@code = NULL}과 같은 방식으로 다뤄질 위험이 있어, 그 경우를
     * 아예 분리해 처리한다.</p>
     */
    private BooleanExpression subscribedByMeExpression(UUID requestUserId) {
        if (requestUserId == null) {
            return Expressions.asBoolean(false);
        }

        return JPAExpressions.selectOne()
                .from(subscribe)
                .where(
                        subscribe.interest.eq(interest),
                        subscribe.userId.eq(requestUserId),
                        subscribe.visibilityStatus.eq(ACTIVE)
                )
                .exists();
    }

    private BooleanExpression searchCondition(String keywordText) {
        if (!StringUtils.hasText(keywordText)) {
            return null;
        }

        BooleanExpression nameMatches = interest.name.containsIgnoreCase(keywordText);
        BooleanExpression keywordMatches = JPAExpressions.selectOne()
                .from(keyword)
                .where(
                        keyword.interest.eq(interest),
                        keyword.keyword.containsIgnoreCase(keywordText)
                )
                .exists();

        return nameMatches.or(keywordMatches);
    }

    /**
     * 커서 기반(keyset) 페이지네이션 조건을 만든다.
     *
     * <p>정렬 기준 값(주 기준), 생성 시각(보조 기준), id(3차 기준) 순으로 사전식(lexicographic)
     * 비교를 한다. 주 기준이 커서보다 "다음" 쪽이면 그대로 통과, 주 기준이 커서와 같으면
     * 생성 시각을 비교하고, 생성 시각까지 같으면 마지막으로 id를 비교한다. 세 기준 모두
     * 같은 방향으로 비교해야, 어느 조합으로 값이 겹치더라도 페이지가 진행될수록 항상 "다음"
     * 쪽으로만 나아가는 유일한 순서가 유지된다.</p>
     *
     * <p>id는 UUID라 그 자체로 크고 작음에 비즈니스적인 의미는 없지만, 항상 유일하므로
     * 정렬 기준과 생성 시각이 모두 같은 항목이 있더라도 순서를 확정적으로 정해준다.
     * 그래서 정렬 기준·생성 시각까지 같은 항목이 페이지 경계에 걸려도 커서가 항목을
     * 건너뛰거나 중복으로 돌려주는 일이 없다. 이 3차 기준값은 곧 {@code cursor} 자신이므로
     * 별도 파라미터로 받지 않는다.</p>
     *
     * @param anchor {@link #resolveAnchor}가 조회한 앵커 행 값. 첫 페이지 조회라 앵커가
     *               없으면 {@code null}이고, 이때는 필터 없이 처음부터 조회한다
     */
    private BooleanExpression keysetCondition(
            InterestOrderBy orderBy,
            Sort.Direction direction,
            AnchorRow anchor,
            LocalDateTime after,
            UUID cursor,
            NumberExpression<Long> subscriberCountExpr
    ) {
        if (anchor == null) {
            return null;
        }

        if (orderBy == InterestOrderBy.NAME) {
            BooleanExpression primaryEq = interest.name.eq(anchor.name());

            if (direction.isAscending()) {
                return interest.name.gt(anchor.name())
                        .or(primaryEq.and(interest.createdAt.gt(after)))
                        .or(primaryEq.and(interest.createdAt.eq(after)).and(interest.id.gt(cursor)));
            }

            return interest.name.lt(anchor.name())
                    .or(primaryEq.and(interest.createdAt.lt(after)))
                    .or(primaryEq.and(interest.createdAt.eq(after)).and(interest.id.lt(cursor)));
        }

        long cursorCount = anchor.subscriberCount();
        BooleanExpression primaryEq = subscriberCountExpr.eq(cursorCount);

        if (direction.isAscending()) {
            return subscriberCountExpr.gt(cursorCount)
                    .or(primaryEq.and(interest.createdAt.gt(after)))
                    .or(primaryEq.and(interest.createdAt.eq(after)).and(interest.id.gt(cursor)));
        }

        return subscriberCountExpr.lt(cursorCount)
                .or(primaryEq.and(interest.createdAt.lt(after)))
                .or(primaryEq.and(interest.createdAt.eq(after)).and(interest.id.lt(cursor)));
    }

    private OrderSpecifier<?>[] orderSpecifiers(
            InterestOrderBy orderBy,
            Sort.Direction direction,
            NumberExpression<Long> subscriberCountExpr
    ) {
        boolean ascending = direction.isAscending();

        OrderSpecifier<?> primary = primaryOrderSpecifier(orderBy, ascending, subscriberCountExpr);
        OrderSpecifier<?> tiebreaker = ascending ? interest.createdAt.asc() : interest.createdAt.desc();
        OrderSpecifier<?> idTiebreaker = ascending ? interest.id.asc() : interest.id.desc();

        return new OrderSpecifier<?>[] {primary, tiebreaker, idTiebreaker};
    }

    /**
     * 정렬 기준(orderBy)에 따라 실제로 정렬에 쓸 표현식을 고른 뒤, 방향(ascending)에 맞춰
     * asc/desc를 적용한다.
     *
     * <p>orderBy 분기와 ascending 분기를 하나의 중첩 삼항 연산자로 처리하면 "이름 기준
     * 오름차순"인지 "구독자 수 기준 내림차순"인지를 한 표현식 안에서 바로 구분하기 어려워,
     * orderBy 갈래를 먼저 early return으로 나눈다.</p>
     */
    private OrderSpecifier<?> primaryOrderSpecifier(
            InterestOrderBy orderBy,
            boolean ascending,
            NumberExpression<Long> subscriberCountExpr
    ) {
        if (orderBy == InterestOrderBy.NAME) {
            return ascending ? interest.name.asc() : interest.name.desc();
        }

        return ascending ? subscriberCountExpr.asc() : subscriberCountExpr.desc();
    }
}
