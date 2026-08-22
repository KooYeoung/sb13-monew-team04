package com.codeit.sb13.monew.article.repository;

import static com.codeit.sb13.monew.article.domain.QArticle.article;
import static com.codeit.sb13.monew.article.domain.QArticleView.articleView;
import static com.codeit.sb13.monew.user.domain.QUser.user;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.repository.dto.ArticleSearchCondition;
import com.codeit.sb13.monew.article.repository.dto.ArticleSearchRow;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class ArticleRepositoryCustomImpl implements ArticleRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<ArticleSearchRow> search(ArticleSearchCondition condition) {
        NumberExpression<Long> viewCountExpr = viewCountExpression();
        BooleanExpression viewedByMeExpr = viewedByMeExpression(condition.requestUserId());

        return queryFactory
                .select(Projections.constructor(
                        // viewCountExpr는 서브쿼리를 감싼 표현식이라 getType()이 Long이 아니라 Object로 소실된다.
                        ArticleSearchRow.class, article, viewCountExpr.longValue(), viewedByMeExpr))
                .from(article)
                .where(
                        article.deletedAt.isNull(),
                        keywordContains(condition.keyword()),
                        sourceIn(condition.sourceIn()),
                        publishDateGoe(condition.publishDateFrom()),
                        publishDateLoe(condition.publishDateTo())
                )
                .orderBy(article.date.desc(), article.createdAt.desc(), article.id.desc())
                .fetch();
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