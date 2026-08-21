package com.codeit.sb13.monew.interest.repository;

import static com.codeit.sb13.monew.interest.domain.QInterest.interest;
import static com.codeit.sb13.monew.interest.domain.QSubscribe.subscribe;

import com.codeit.sb13.monew.global.exception.interest.InterestSearchConditionInvalidException;
import com.codeit.sb13.monew.interest.domain.QKeyword;
import com.codeit.sb13.monew.interest.repository.dto.InterestSearchCondition;
import com.codeit.sb13.monew.interest.repository.dto.InterestSearchPage;
import com.codeit.sb13.monew.interest.repository.dto.InterestSearchRow;
import com.codeit.sb13.monew.interest.service.dto.InterestOrderBy;
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
        String cursor = condition.cursor();
        LocalDateTime after = condition.after();
        UUID idAfter = condition.idAfter();
        int limit = condition.limit();
        UUID requestUserId = condition.requestUserId();

        NumberExpression<Long> subscriberCountExpr = subscriberCountExpression();
        BooleanExpression subscribedByMeExpr = subscribedByMeExpression(requestUserId);
        BooleanExpression searchCondition = searchCondition(keywordText);
        BooleanExpression keysetCondition =
                keysetCondition(orderBy, direction, cursor, after, idAfter, subscriberCountExpr);

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
        // 관심사들의 id를 묶어 IN 쿼리 한 번으로 지연 로딩된다(InterestServiceImpl#search 참고).

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

    private NumberExpression<Long> subscriberCountExpression() {
        return Expressions.asNumber(
                JPAExpressions.select(subscribe.count())
                        .from(subscribe)
                        .where(subscribe.interest.eq(interest))
        );
    }

    /**
     * 요청자가 이 관심사를 구독 중인지 여부를 서브쿼리로 계산한다.
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
                .where(subscribe.interest.eq(interest), subscribe.userId.eq(requestUserId))
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
     * 비교를 한다. 주 기준이 커서 값보다 "다음" 쪽이면 그대로 통과, 주 기준이 커서와 같으면
     * 생성 시각을 비교하고, 생성 시각까지 같으면 마지막으로 id를 비교한다. 세 기준 모두
     * 같은 방향으로 비교해야, 어느 조합으로 값이 겹치더라도 페이지가 진행될수록 항상 "다음"
     * 쪽으로만 나아가는 유일한 순서가 유지된다.</p>
     *
     * <p>id는 UUID라 그 자체로 크고 작음에 비즈니스적인 의미는 없지만, 항상 유일하므로
     * 정렬 기준과 생성 시각이 모두 같은 항목이 있더라도 순서를 확정적으로 정해준다.
     * 그래서 정렬 기준·생성 시각까지 같은 항목이 페이지 경계에 걸려도 커서가 항목을
     * 건너뛰거나 중복으로 돌려주는 일이 없다.</p>
     */
    private BooleanExpression keysetCondition(
            InterestOrderBy orderBy,
            Sort.Direction direction,
            String cursor,
            LocalDateTime after,
            UUID idAfter,
            NumberExpression<Long> subscriberCountExpr
    ) {
        if (!StringUtils.hasText(cursor) || after == null || idAfter == null) {
            return null;
        }

        if (orderBy == InterestOrderBy.NAME) {
            BooleanExpression primaryEq = interest.name.eq(cursor);

            if (direction.isAscending()) {
                return interest.name.gt(cursor)
                        .or(primaryEq.and(interest.createdAt.gt(after)))
                        .or(primaryEq.and(interest.createdAt.eq(after)).and(interest.id.gt(idAfter)));
            }

            return interest.name.lt(cursor)
                    .or(primaryEq.and(interest.createdAt.lt(after)))
                    .or(primaryEq.and(interest.createdAt.eq(after)).and(interest.id.lt(idAfter)));
        }

        long cursorCount = parseCursorAsCount(cursor);
        BooleanExpression primaryEq = subscriberCountExpr.eq(cursorCount);

        if (direction.isAscending()) {
            return subscriberCountExpr.gt(cursorCount)
                    .or(primaryEq.and(interest.createdAt.gt(after)))
                    .or(primaryEq.and(interest.createdAt.eq(after)).and(interest.id.gt(idAfter)));
        }

        return subscriberCountExpr.lt(cursorCount)
                .or(primaryEq.and(interest.createdAt.lt(after)))
                .or(primaryEq.and(interest.createdAt.eq(after)).and(interest.id.lt(idAfter)));
    }

    private long parseCursorAsCount(String cursor) {
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            throw new InterestSearchConditionInvalidException("구독자 수 기준 커서 값이 올바르지 않습니다: " + cursor);
        }
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
