package com.codeit.sb13.monew.article.repository;

import static com.codeit.sb13.monew.article.domain.QArticle.article;
import static com.codeit.sb13.monew.article.domain.QArticleView.articleView;
import static com.codeit.sb13.monew.comment.domain.QComment.comment;
import static com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus.ACTIVE;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.interest.domain.QKeyword;
import com.codeit.sb13.monew.article.repository.dto.ArticleSearchCondition;
import com.codeit.sb13.monew.article.repository.dto.ArticleSearchPage;
import com.codeit.sb13.monew.article.repository.dto.ArticleSearchRow;
import com.codeit.sb13.monew.article.service.dto.ArticleOrderBy;
import com.codeit.sb13.monew.global.exception.article.ArticleSearchConditionInvalidException;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class ArticleRepositoryCustomImpl implements ArticleRepositoryCustom {

    private static final QKeyword interestKeyword = new QKeyword("interestKeyword");

    private final JPAQueryFactory queryFactory;

    @Override
    public ArticleSearchPage search(ArticleSearchCondition condition) {
        NumberExpression<Long> commentCountExpr = commentCountExpression();
        NumberExpression<Long> viewCountExpr = viewCountExpression();
        BooleanExpression viewedByMeExpr = viewedByMeExpression(condition.requestUserId());

        BooleanExpression[] filters = {
                article.deletedAt.isNull(),
                keywordContains(condition.keyword()),
                interestMatches(condition.interestId()),
                sourceIn(condition.sourceIn()),
                publishDateGoe(condition.publishDateFrom()),
                publishDateLoe(condition.publishDateTo())
        };
        // cursor(이전 페이지 마지막 기사의 id)로 그 행을 다시 조회해, 키셋 비교에 쓸
        // "현재" 정렬 기준 값을 가져온다.
        AnchorRow anchor = resolveAnchor(condition, commentCountExpr, viewCountExpr);
        BooleanExpression keysetCondition = keysetCondition(
                condition, anchor, commentCountExpr, viewCountExpr);

        // 다음 페이지 존재 여부를 별도 count 쿼리 없이 판단하기 위해 요청한 limit보다 하나 더 가져온다.
        List<ArticleSearchRow> rows = queryFactory
                .select(Projections.constructor(
                        // 두 카운트 표현식은 서브쿼리를 감싼 것이라 getType()이 Long이 아니라 Object로 소실된다.
                        ArticleSearchRow.class, article,
                        commentCountExpr.longValue(), viewCountExpr.longValue(), viewedByMeExpr))
                .from(article)
                .where(filters)
                .where(keysetCondition)
                .orderBy(orderSpecifiers(
                        condition.orderBy(), condition.direction(),
                        commentCountExpr, viewCountExpr))
                .limit(condition.limit() + 1L)
                .fetch();

        boolean hasNext = rows.size() > condition.limit();
        List<ArticleSearchRow> pageRows = hasNext ? rows.subList(0, condition.limit()) : rows;

        // 전체 건수는 커서 조건을 빼고 센다. 커서는 페이지 위치를 나타낼 뿐 검색 조건이 아니다.
        Long totalElements = queryFactory
                .select(article.count())
                .from(article)
                .where(filters)
                .fetchOne();

        return new ArticleSearchPage(
                pageRows, hasNext, totalElements == null ? 0L : totalElements);
    }

    private NumberExpression<Long> commentCountExpression() {
        return Expressions.asNumber(
                JPAExpressions.select(comment.count())
                        .from(comment)
                        .where(
                                comment.article.eq(article),
                                comment.visibilityStatus.eq(ACTIVE)
                        )
        );
    }

    private NumberExpression<Long> viewCountExpression() {
        return Expressions.asNumber(
                JPAExpressions.select(articleView.count())
                        .from(articleView)
                        .where(
                                articleView.article.eq(article),
                                articleView.visibilityStatus.eq(ACTIVE)
                        )
        );
    }

    // 요청자가 이 기사를 조회한 적 있는지를 서브쿼리로 계산한다.
    private BooleanExpression viewedByMeExpression(UUID requestUserId) {
        if (requestUserId == null) {
            return Expressions.asBoolean(false);
        }

        return JPAExpressions.selectOne()
                .from(articleView)
                .where(articleView.article.eq(article), articleView.user.id.eq(requestUserId))
                .exists();
    }

    /**
     * {@code cursor}(이전 페이지 마지막 기사의 id)로 그 행을 다시 조회해, 키셋 비교에 쓸
     * "현재" 정렬 기준 값을 가져온다.
     *
     * <p>커서 문자열에 정렬 기준 값을 담아 그대로 쓰던 방식은, 그 값이 이전 페이지 응답을
     * 만든 시점의 스냅샷이라는 한계가 있다. {@code viewCount}/{@code commentCount}는 페이지
     * 요청 사이에 바뀌므로 클라이언트가 들고 있는 값이 실제 값과 어긋난다. 여기서는 매 요청
     * 앵커 행을 다시 조회해 그 시점 기준으로 비교한다.</p>
     *
     * <p>다만 이 방식도 완전하지는 않다. 앵커 자신의 정렬 기준 값이 변하면 경계가 그만큼
     * 움직여, 값이 변하지 않은 다른 기사까지 중복되거나 빠질 수 있다. 정렬 키가 변하는 한
     * 무상태 커서로는 완전히 풀 수 없고, 서버가 랭킹 스냅샷을 유지해야 한다. 관심사 목록도
     * 같은 방식이라 세 목록의 커서 계약을 맞추는 쪽을 택했다.</p>
     *
     * <p>{@code cursor}와 {@code after}가 둘 다 없으면 첫 페이지 조회로 보고 조회를 생략한다.
     * 둘 중 하나만 있으면 커서를 일부만 보낸 잘못된 요청이라, 조용히 첫 페이지를 돌려주는
     * 대신(클라이언트에는 이전 페이지 항목이 중복으로 보인다) 예외를 던진다. {@code cursor}로
     * 조회했는데 행이 없으면 그 사이 기사가 물리 삭제된 것이므로 이어보기 위치를 계산할 수
     * 없다는 뜻으로 예외를 던진다.</p>
     */
    private AnchorRow resolveAnchor(
            ArticleSearchCondition condition,
            NumberExpression<Long> commentCountExpr,
            NumberExpression<Long> viewCountExpr
    ) {
        UUID cursor = condition.cursor();
        LocalDateTime after = condition.after();

        if (cursor == null && after == null) {
            return null;
        }

        if (cursor == null || after == null) {
            throw new ArticleSearchConditionInvalidException("cursor와 after는 함께 전달해야 합니다.");
        }

        if (condition.orderBy() == ArticleOrderBy.PUBLISH_DATE) {
            LocalDateTime date = queryFactory
                    .select(article.date)
                    .from(article)
                    .where(article.id.eq(cursor))
                    .fetchOne();

            if (date == null) {
                throw new ArticleSearchConditionInvalidException(
                        "커서가 가리키는 기사를 더 이상 찾을 수 없습니다: " + cursor);
            }
            return new AnchorRow(date, null);
        }

        NumberExpression<Long> countExpr = condition.orderBy() == ArticleOrderBy.COMMENT_COUNT
                ? commentCountExpr : viewCountExpr;
        // 카운트 표현식은 서브쿼리를 감싼 것이라 제네릭 타입이 Object로 소실된다.
        // longValue()로 복원해야 fetchOne()이 Long을 돌려준다.
        Long count = queryFactory
                .select(countExpr.longValue())
                .from(article)
                .where(article.id.eq(cursor))
                .fetchOne();

        if (count == null) {
            throw new ArticleSearchConditionInvalidException(
                    "커서가 가리키는 기사를 더 이상 찾을 수 없습니다: " + cursor);
        }

        return new AnchorRow(null, count);
    }

    /**
     * {@link #resolveAnchor}가 다시 조회한 앵커 행의 값.
     *
     * <p>{@code date}는 발행일 정렬일 때만, {@code count}는 집계값 정렬일 때만 채워진다.
     * 쓰지 않는 쪽을 굳이 조회하면 서브쿼리가 한 번 더 나가므로 정렬 기준에 필요한 값만
     * 담는다.</p>
     */
    private record AnchorRow(LocalDateTime date, Long count) {
    }

    private BooleanExpression keysetCondition(
            ArticleSearchCondition condition,
            AnchorRow anchor,
            NumberExpression<Long> commentCountExpr,
            NumberExpression<Long> viewCountExpr
    ) {
        if (anchor == null) {
            return null;
        }

        boolean ascending = condition.direction().isAscending();
        LocalDateTime after = condition.after();
        UUID cursor = condition.cursor();

        if (condition.orderBy() == ArticleOrderBy.PUBLISH_DATE) {
            LocalDateTime anchorDate = anchor.date();
            return keyset(article.date.eq(anchorDate),
                    ascending ? article.date.gt(anchorDate) : article.date.lt(anchorDate),
                    ascending, after, cursor);
        }

        NumberExpression<Long> countExpr = condition.orderBy() == ArticleOrderBy.COMMENT_COUNT
                ? commentCountExpr : viewCountExpr;
        long anchorCount = anchor.count();

        return keyset(countExpr.eq(anchorCount),
                ascending ? countExpr.gt(anchorCount) : countExpr.lt(anchorCount),
                ascending, after, cursor);
    }

    /**
     * 주 기준 비교가 정해진 뒤의 공통 부분(보조 기준, 3차 기준)을 조립한다.
     *
     * @param primaryEq 주 기준이 커서 값과 같은지
     * @param primaryAdvances 주 기준이 커서보다 "다음" 쪽인지
     */
    private BooleanExpression keyset(
            BooleanExpression primaryEq,
            BooleanExpression primaryAdvances,
            boolean ascending,
            LocalDateTime after,
            UUID cursor
    ) {
        BooleanExpression createdAtAdvances =
                ascending ? article.createdAt.gt(after) : article.createdAt.lt(after);
        BooleanExpression idAdvances =
                ascending ? article.id.gt(cursor) : article.id.lt(cursor);

        return primaryAdvances
                .or(primaryEq.and(createdAtAdvances))
                .or(primaryEq.and(article.createdAt.eq(after)).and(idAdvances));
    }

    /**
     * 정렬 기준 값, 생성 시각, id 순으로 3단 정렬한다.
     *
     * <p>정렬 기준 값만으로는 동점인 기사들의 순서가 확정되지 않는다. 순서가 매 조회마다
     * 달라지면 커서 페이지네이션에서 항목이 중복되거나 누락되므로, 항상 유일한
     * {@code id}까지 내려가 순서를 확정한다. 세 기준을 모두 같은 방향으로 정렬해야
     * 페이지가 진행될수록 한쪽으로만 나아가는 유일한 순서가 유지된다.
     *
     * <p>MID4-109 관심사 목록 조회({@code InterestRepositoryCustomImpl})와 같은 구조다.
     * API마다 페이지네이션 동작이 달라지지 않도록 맞췄다.
     */
    private OrderSpecifier<?>[] orderSpecifiers(
            ArticleOrderBy orderBy,
            Sort.Direction direction,
            NumberExpression<Long> commentCountExpr,
            NumberExpression<Long> viewCountExpr
    ) {
        boolean ascending = direction.isAscending();

        OrderSpecifier<?> primary =
                primaryOrderSpecifier(orderBy, ascending, commentCountExpr, viewCountExpr);
        OrderSpecifier<?> tiebreaker = ascending ? article.createdAt.asc() : article.createdAt.desc();
        OrderSpecifier<?> idTiebreaker = ascending ? article.id.asc() : article.id.desc();

        return new OrderSpecifier<?>[] {primary, tiebreaker, idTiebreaker};
    }

    private OrderSpecifier<?> primaryOrderSpecifier(
            ArticleOrderBy orderBy,
            boolean ascending,
            NumberExpression<Long> commentCountExpr,
            NumberExpression<Long> viewCountExpr
    ) {
        if (orderBy == ArticleOrderBy.COMMENT_COUNT) {
            return ascending ? commentCountExpr.asc() : commentCountExpr.desc();
        }
        if (orderBy == ArticleOrderBy.VIEW_COUNT) {
            return ascending ? viewCountExpr.asc() : viewCountExpr.desc();
        }

        return ascending ? article.date.asc() : article.date.desc();
    }

    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return article.title.containsIgnoreCase(keyword)
                .or(article.summary.containsIgnoreCase(keyword));
    }

    private BooleanExpression interestMatches(UUID interestId) {
        if (interestId == null) {
            return null;
        }

        return JPAExpressions.selectOne()
                .from(interestKeyword)
                .where(
                        interestKeyword.interest.id.eq(interestId),
                        article.title.containsIgnoreCase(interestKeyword.keyword)
                                .or(article.summary.containsIgnoreCase(interestKeyword.keyword))
                )
                .exists();
    }

    private BooleanExpression sourceIn(List<ArticleSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        return article.source.in(sources);
    }

    private BooleanExpression publishDateGoe(LocalDateTime from) {
        return from == null ? null : article.date.goe(from);
    }

    private BooleanExpression publishDateLoe(LocalDateTime to) {
        return to == null ? null : article.date.loe(to);
    }
}
