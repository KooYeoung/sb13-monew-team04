package com.codeit.sb13.monew.activity.service;

import static com.codeit.sb13.monew.article.domain.QArticleView.articleView;
import static com.codeit.sb13.monew.comment.domain.QComment.comment;
import static com.codeit.sb13.monew.comment.domain.QCommentLike.commentLike;
import static com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus.ACTIVE;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 삭제 이벤트에 따라 활동 관련 row의 노출 상태를 일괄 갱신한다.
 *
 * <p>노출 상태와 대상 조건은 항상 {@link ActivityDeletionTarget} 기준으로 함께 결정한다.
 * 외부 서비스는 {@code ActivityVisibilityStatus}를 직접 넘기지 않고 삭제 이벤트별 명시 메서드를 호출한다.
 * 현재 MID4-222 범위에서는 기사 삭제 이벤트만 지원한다.</p>
 */
@Component
@RequiredArgsConstructor
public class ActivityVisibilityUpdater {

    private final EntityManager entityManager;
    private final JPAQueryFactory queryFactory;

    /**
     * 기사 삭제 시 해당 기사와 연결된 활성 활동 row를 {@code ARTICLE_DELETED} 상태로 숨긴다.
     *
     * <p>이 메서드는 bulk update 이후 오래된 활동 엔티티 상태를 재사용하지 않도록
     * 영속성 컨텍스트를 초기화한다. 같은 트랜잭션에서 이미 조회한 엔티티 상태가 필요하면
     * 이 메서드 호출 후 다시 조회해야 한다.</p>
     *
     * @param articleId 삭제된 기사 id
     * @return 활동 테이블별 갱신 건수
     */
    @Transactional
    public ArticleActivityVisibilityUpdateResult hideActiveByDeletedArticle(UUID articleId) {
        return hideActive(ActivityDeletionTarget.deletedArticle(articleId));
    }

    private ArticleActivityVisibilityUpdateResult hideActive(ActivityDeletionTarget target) {
        entityManager.flush();

        long articleViewCount = hideArticleViews(target);
        long commentCount = hideComments(target);
        long commentLikeCount = hideCommentLikes(target);

        ArticleActivityVisibilityUpdateResult result = new ArticleActivityVisibilityUpdateResult(
                articleViewCount,
                commentCount,
                commentLikeCount
        );
        entityManager.clear();

        return result;
    }

    private long hideArticleViews(ActivityDeletionTarget target) {
        return queryFactory
                .update(articleView)
                .set(articleView.visibilityStatus, target.targetStatus())
                .where(
                        articleViewTargetCondition(target),
                        articleView.visibilityStatus.eq(ACTIVE)
                )
                .execute();
    }

    private long hideComments(ActivityDeletionTarget target) {
        return queryFactory
                .update(comment)
                .set(comment.visibilityStatus, target.targetStatus())
                .where(
                        commentTargetCondition(target),
                        comment.visibilityStatus.eq(ACTIVE)
                )
                .execute();
    }

    private long hideCommentLikes(ActivityDeletionTarget target) {
        return queryFactory
                .update(commentLike)
                .set(commentLike.visibilityStatus, target.targetStatus())
                .where(
                        commentLikeTargetCondition(target),
                        commentLike.visibilityStatus.eq(ACTIVE)
                )
                .execute();
    }

    private BooleanExpression articleViewTargetCondition(ActivityDeletionTarget target) {
        // TODO: USER 삭제 작업 진행 시 articleView 대상 조건 검토 필요.
        return articleView.article.id.eq(target.targetId());
    }

    private BooleanExpression commentTargetCondition(ActivityDeletionTarget target) {
        // TODO: USER 삭제 작업 진행 시 comment 대상 조건 검토 필요.
        // TODO: COMMENT 삭제 작업 진행 시 comment 대상 조건 검토 필요.
        return comment.article.id.eq(target.targetId());
    }

    private BooleanExpression commentLikeTargetCondition(ActivityDeletionTarget target) {
        // TODO: USER 삭제 작업 진행 시 commentLike 대상 조건 검토 필요.
        // TODO: COMMENT 삭제 작업 진행 시 commentLike 대상 조건 검토 필요.
        return commentLike.comment.article.id.eq(target.targetId());
    }
}
