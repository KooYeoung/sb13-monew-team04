package com.codeit.sb13.monew.article.repository;

import static com.codeit.sb13.monew.article.domain.QArticle.article;
import static com.codeit.sb13.monew.article.domain.QArticleView.articleView;
import static com.codeit.sb13.monew.comment.domain.QComment.comment;
import static com.codeit.sb13.monew.user.domain.QUser.user;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.repository.dto.ArticleSearchCondition;
import com.codeit.sb13.monew.article.repository.dto.ArticleSearchPage;
import com.codeit.sb13.monew.article.repository.dto.ArticleSearchRow;
import com.codeit.sb13.monew.article.service.dto.ArticleOrderBy;
import com.codeit.sb13.monew.global.exception.article.ArticleSearchConditionInvalidException;
import com.codeit.sb13.monew.user.domain.QUser;
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
import java.time.format.DateTimeParseException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class ArticleRepositoryCustomImpl implements ArticleRepositoryCustom {

    private static final QUser commentUser = new QUser("commentUser");

    private final JPAQueryFactory queryFactory;

    @Override
    public ArticleSearchPage search(ArticleSearchCondition condition) {
        NumberExpression<Long> commentCountExpr = commentCountExpression();
        NumberExpression<Long> viewCountExpr = viewCountExpression();
        BooleanExpression viewedByMeExpr = viewedByMeExpression(condition.requestUserId());

        BooleanExpression[] filters = {
                article.deletedAt.isNull(),
                keywordContains(condition.keyword()),
                sourceIn(condition.sourceIn()),
                publishDateGoe(condition.publishDateFrom()),
                publishDateLoe(condition.publishDateTo())
        };
        BooleanExpression keysetCondition = keysetCondition(
                condition, commentCountExpr, viewCountExpr);

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
                        .join(comment.user, commentUser)
                        .where(
                                comment.article.eq(article),
                                comment.deletedAt.isNull(),
                                commentUser.deletedAt.isNull()
                        )
        );
    }

    private NumberExpression<Long> viewCountExpression() {
        return Expressions.asNumber(
                JPAExpressions.select(articleView.count())
                        .from(articleView)
                        .join(articleView.user, user)
                        .where(articleView.article.eq(article), user.deletedAt.isNull())
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
     * 커서 기반(keyset) 페이지네이션 조건을 만든다.
     *
     * <p>정렬 기준 값(주 기준), 생성 시각(보조 기준), id(3차 기준) 순으로 사전식 비교를 한다.
     * 주 기준이 커서보다 "다음" 쪽이면 통과, 주 기준이 같으면 생성 시각을 보고, 생성 시각까지
     * 같으면 id를 본다. 세 기준을 모두 같은 방향으로 비교해야 값이 어떻게 겹치든 페이지가
     * 항상 한쪽으로만 나아가는 유일한 순서가 유지된다.
     *
     * <p>id는 UUID라 크고 작음에 비즈니스적 의미는 없지만 항상 유일하므로, 정렬 기준과
     * 생성 시각이 모두 같은 기사가 페이지 경계에 걸려도 건너뛰거나 중복으로 반환되지 않는다.
     */
    private BooleanExpression keysetCondition(
            ArticleSearchCondition condition,
            NumberExpression<Long> commentCountExpr,
            NumberExpression<Long> viewCountExpr
    ) {
        String cursor = condition.cursor();
        LocalDateTime after = condition.after();
        UUID idAfter = condition.idAfter();

        // 세 값은 하나의 단위다. 일부만 오면 첫 페이지를 다시 돌려주게 되어
        // 클라이언트가 같은 항목을 두 번 받는다. 조용히 넘기지 않고 거부한다.
        boolean noneProvided = !StringUtils.hasText(cursor) && after == null && idAfter == null;
        if (noneProvided) {
            return null;
        }
        if (!StringUtils.hasText(cursor) || after == null || idAfter == null) {
            throw new ArticleSearchConditionInvalidException(
                    "cursor, after, idAfter는 함께 전달해야 합니다.");
        }

        boolean ascending = condition.direction().isAscending();

        if (condition.orderBy() == ArticleOrderBy.PUBLISH_DATE) {
            LocalDateTime cursorDate = parseCursorAsDateTime(cursor);
            return keyset(article.date.eq(cursorDate),
                    ascending ? article.date.gt(cursorDate) : article.date.lt(cursorDate),
                    ascending, after, idAfter);
        }

        NumberExpression<Long> countExpr =
                condition.orderBy() == ArticleOrderBy.COMMENT_COUNT ? commentCountExpr : viewCountExpr;
        long cursorCount = parseCursorAsCount(cursor);

        return keyset(countExpr.eq(cursorCount),
                ascending ? countExpr.gt(cursorCount) : countExpr.lt(cursorCount),
                ascending, after, idAfter);
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
            UUID idAfter
    ) {
        BooleanExpression createdAtAdvances =
                ascending ? article.createdAt.gt(after) : article.createdAt.lt(after);
        BooleanExpression idAdvances =
                ascending ? article.id.gt(idAfter) : article.id.lt(idAfter);

        return primaryAdvances
                .or(primaryEq.and(createdAtAdvances))
                .or(primaryEq.and(article.createdAt.eq(after)).and(idAdvances));
    }

    private LocalDateTime parseCursorAsDateTime(String cursor) {
        try {
            return LocalDateTime.parse(cursor);
        } catch (DateTimeParseException e) {
            throw new ArticleSearchConditionInvalidException(
                    "발행일 기준 커서 값이 올바르지 않습니다: " + cursor);
        }
    }

    private long parseCursorAsCount(String cursor) {
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            throw new ArticleSearchConditionInvalidException(
                    "집계값 기준 커서 값이 올바르지 않습니다: " + cursor);
        }
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