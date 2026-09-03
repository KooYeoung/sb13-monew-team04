package com.codeit.sb13.monew.activity.outbox.service;

import static com.codeit.sb13.monew.article.domain.QArticleView.articleView;
import static com.codeit.sb13.monew.comment.domain.QComment.comment;
import static com.codeit.sb13.monew.comment.domain.QCommentLike.commentLike;
import static com.codeit.sb13.monew.interest.domain.QSubscribe.subscribe;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventType;
import com.codeit.sb13.monew.activity.outbox.payload.ActivityProjectionKeyPayload;
import com.codeit.sb13.monew.activity.outbox.payload.ProjectionImpact;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 삭제나 사용자 표시값 변경 전에 영향을 받는 projection 논리 키를 RDB에서 수집한다.
 *
 * <p>원본 관계가 제거된 뒤에는 worker가 과거 MongoDB 문서뿐 아니라 아직 생성되지
 * 않은 문서의 논리 키도 알아낼 수 없다. 따라서 생산 트랜잭션에서 먼저 이 결과를
 * payload에 담아, 최신 삭제 이벤트가 hidden/tombstone 문서를 물질화하도록 한다.</p>
 */
@Component
@RequiredArgsConstructor
public class OutboxProjectionImpactReader {

    private final JPAQueryFactory queryFactory;

    @Transactional(readOnly = true)
    public ProjectionImpact forComment(UUID commentId) {
        List<ActivityProjectionKeyPayload> keys = new ArrayList<>();
        keys.addAll(commentAuthors(comment.id.eq(commentId)));
        keys.addAll(commentLikes(commentLike.comment.id.eq(commentId)));
        return new ProjectionImpact(keys, List.of(commentId));
    }

    @Transactional(readOnly = true)
    public ProjectionImpact forArticle(UUID articleId) {
        List<CommentUserRow> comments = commentRows(comment.article.id.eq(articleId));
        List<ActivityProjectionKeyPayload> keys = new ArrayList<>();
        keys.addAll(articleViews(articleView.article.id.eq(articleId)));
        keys.addAll(comments.stream()
                .map(row -> key(row.userId(), OutboxEventType.COMMENT_WRITTEN, row.commentId()))
                .toList());
        keys.addAll(commentLikes(commentLike.comment.article.id.eq(articleId)));
        return new ProjectionImpact(
                keys,
                comments.stream().map(CommentUserRow::commentId).toList()
        );
    }

    @Transactional(readOnly = true)
    public ProjectionImpact forInterest(UUID interestId) {
        return new ProjectionImpact(
                subscriptions(subscribe.interest.id.eq(interestId)),
                List.of()
        );
    }

    @Transactional(readOnly = true)
    public ProjectionImpact forUser(UUID userId) {
        List<CommentUserRow> comments = commentRows(comment.user.id.eq(userId));
        List<ActivityProjectionKeyPayload> keys = new ArrayList<>();
        keys.addAll(subscriptions(subscribe.userId.eq(userId)));
        keys.addAll(articleViews(articleView.user.id.eq(userId)));
        keys.addAll(comments.stream()
                .map(row -> key(userId, OutboxEventType.COMMENT_WRITTEN, row.commentId()))
                .toList());
        keys.addAll(commentLikes(commentLike.likedBy.id.eq(userId)));
        keys.addAll(commentLikes(commentLike.comment.user.id.eq(userId)));
        return new ProjectionImpact(
                keys,
                comments.stream().map(CommentUserRow::commentId).toList()
        );
    }

    @Transactional(readOnly = true)
    public ProjectionImpact forNicknameChange(UUID userId) {
        return new ProjectionImpact(
                List.of(),
                commentRows(comment.user.id.eq(userId)).stream()
                        .map(CommentUserRow::commentId)
                        .toList()
        );
    }

    private List<ActivityProjectionKeyPayload> subscriptions(
            com.querydsl.core.types.Predicate predicate
    ) {
        return queryFactory
                .select(Projections.constructor(
                        TargetUserRow.class,
                        subscribe.interest.id,
                        subscribe.userId
                ))
                .from(subscribe)
                .where(predicate)
                .fetch().stream()
                .map(row -> key(row.userId(), OutboxEventType.INTEREST_SUBSCRIBED,
                        row.targetId()))
                .toList();
    }

    private List<ActivityProjectionKeyPayload> articleViews(
            com.querydsl.core.types.Predicate predicate
    ) {
        return queryFactory
                .select(Projections.constructor(
                        TargetUserRow.class,
                        articleView.article.id,
                        articleView.user.id
                ))
                .from(articleView)
                .where(predicate)
                .fetch().stream()
                .map(row -> key(row.userId(), OutboxEventType.ARTICLE_VIEWED, row.targetId()))
                .toList();
    }

    private List<ActivityProjectionKeyPayload> commentAuthors(
            com.querydsl.core.types.Predicate predicate
    ) {
        return commentRows(predicate).stream()
                .map(row -> key(row.userId(), OutboxEventType.COMMENT_WRITTEN, row.commentId()))
                .toList();
    }

    private List<CommentUserRow> commentRows(com.querydsl.core.types.Predicate predicate) {
        return queryFactory
                .select(Projections.constructor(
                        CommentUserRow.class,
                        comment.id,
                        comment.user.id
                ))
                .from(comment)
                .where(predicate)
                .fetch();
    }

    private List<ActivityProjectionKeyPayload> commentLikes(
            com.querydsl.core.types.Predicate predicate
    ) {
        return queryFactory
                .select(Projections.constructor(
                        TargetUserRow.class,
                        commentLike.comment.id,
                        commentLike.likedBy.id
                ))
                .from(commentLike)
                .where(predicate)
                .fetch().stream()
                .map(row -> key(row.userId(), OutboxEventType.COMMENT_LIKED, row.targetId()))
                .toList();
    }

    private ActivityProjectionKeyPayload key(
            UUID userId,
            OutboxEventType type,
            UUID targetId
    ) {
        return new ActivityProjectionKeyPayload(userId, type, targetId);
    }

    public record TargetUserRow(UUID targetId, UUID userId) {
    }

    public record CommentUserRow(UUID commentId, UUID userId) {
    }
}
