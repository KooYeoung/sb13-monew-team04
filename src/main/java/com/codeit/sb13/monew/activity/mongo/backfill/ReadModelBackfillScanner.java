package com.codeit.sb13.monew.activity.mongo.backfill;

import static com.codeit.sb13.monew.article.domain.QArticleView.articleView;
import static com.codeit.sb13.monew.comment.domain.QComment.comment;
import static com.codeit.sb13.monew.comment.domain.QCommentLike.commentLike;
import static com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus.ACTIVE;
import static com.codeit.sb13.monew.interest.domain.QSubscribe.subscribe;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxAggregateType;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventType;
import com.codeit.sb13.monew.activity.outbox.payload.ArticleOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.payload.CommentLikeOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.payload.CommentOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.payload.InterestOutboxPayload;
import com.codeit.sb13.monew.user.domain.QUser;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparableExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 활성 RDB 활동 row를 UUID keyset page로 읽어 초기 projection 명령으로 변환한다. */
@Component
@RequiredArgsConstructor
public class ReadModelBackfillScanner {

    private final JPAQueryFactory queryFactory;

    public InitialProjectionPage scan(
            ReadModelBackfillStage stage,
            UUID cursor,
            UUID inclusiveEnd,
            int limit,
            long projectionVersion
    ) {
        List<InitialProjectionEvent> events = switch (stage) {
            case SUBSCRIPTION -> subscriptions(cursor, inclusiveEnd, limit, projectionVersion);
            case COMMENT_WRITTEN -> comments(cursor, inclusiveEnd, limit, projectionVersion);
            case COMMENT_LIKED -> commentLikes(cursor, inclusiveEnd, limit, projectionVersion);
            case ARTICLE_VIEWED -> articleViews(cursor, inclusiveEnd, limit, projectionVersion);
        };
        UUID lastId = events.isEmpty()
                ? null
                : events.get(events.size() - 1).sourceRowId();
        return new InitialProjectionPage(events, lastId);
    }

    private List<InitialProjectionEvent> subscriptions(
            UUID cursor,
            UUID inclusiveEnd,
            int limit,
            long version
    ) {
        QUser subscriber = new QUser("backfillSubscriber");
        return queryFactory
                .select(Projections.constructor(
                        SubscriptionRow.class,
                        subscribe.id,
                        subscribe.interest.id,
                        subscribe.userId,
                        subscribe.createdAt
                ))
                .from(subscribe, subscriber)
                .where(
                        subscribe.visibilityStatus.eq(ACTIVE),
                        subscriber.id.eq(subscribe.userId),
                        subscriber.deletedAt.isNull(),
                        after(subscribe.id, cursor),
                        through(subscribe.id, inclusiveEnd)
                )
                .orderBy(subscribe.id.asc())
                .limit(limit)
                .fetch()
                .stream()
                .map(row -> new InitialProjectionEvent(
                        row.id(),
                        OutboxEventType.INTEREST_SUBSCRIBED,
                        OutboxAggregateType.INTEREST,
                        row.interestId(),
                        row.userId(),
                        new InterestOutboxPayload(OutboxEventAction.SUBSCRIBED),
                        version,
                        row.occurredAt()
                ))
                .toList();
    }

    private List<InitialProjectionEvent> comments(
            UUID cursor,
            UUID inclusiveEnd,
            int limit,
            long version
    ) {
        return queryFactory
                .select(Projections.constructor(
                        CommentRow.class,
                        comment.id,
                        comment.article.id,
                        comment.user.id,
                        comment.createdAt
                ))
                .from(comment)
                .where(
                        comment.visibilityStatus.eq(ACTIVE),
                        comment.deletedAt.isNull(),
                        comment.article.deletedAt.isNull(),
                        comment.user.deletedAt.isNull(),
                        after(comment.id, cursor),
                        through(comment.id, inclusiveEnd)
                )
                .orderBy(comment.id.asc())
                .limit(limit)
                .fetch()
                .stream()
                .map(row -> new InitialProjectionEvent(
                        row.id(),
                        OutboxEventType.COMMENT_WRITTEN,
                        OutboxAggregateType.COMMENT,
                        row.id(),
                        row.userId(),
                        new CommentOutboxPayload(row.articleId(), OutboxEventAction.WRITTEN),
                        version,
                        row.occurredAt()
                ))
                .toList();
    }

    private List<InitialProjectionEvent> commentLikes(
            UUID cursor,
            UUID inclusiveEnd,
            int limit,
            long version
    ) {
        return queryFactory
                .select(Projections.constructor(
                        CommentLikeRow.class,
                        commentLike.id,
                        commentLike.comment.id,
                        commentLike.comment.article.id,
                        commentLike.likedBy.id,
                        commentLike.createdAt
                ))
                .from(commentLike)
                .where(
                        commentLike.visibilityStatus.eq(ACTIVE),
                        commentLike.likedBy.deletedAt.isNull(),
                        commentLike.comment.visibilityStatus.eq(ACTIVE),
                        commentLike.comment.deletedAt.isNull(),
                        commentLike.comment.article.deletedAt.isNull(),
                        commentLike.comment.user.deletedAt.isNull(),
                        after(commentLike.id, cursor),
                        through(commentLike.id, inclusiveEnd)
                )
                .orderBy(commentLike.id.asc())
                .limit(limit)
                .fetch()
                .stream()
                .map(row -> new InitialProjectionEvent(
                        row.id(),
                        OutboxEventType.COMMENT_LIKED,
                        OutboxAggregateType.COMMENT,
                        row.commentId(),
                        row.userId(),
                        new CommentLikeOutboxPayload(row.articleId(), OutboxEventAction.LIKED),
                        version,
                        row.occurredAt()
                ))
                .toList();
    }

    private List<InitialProjectionEvent> articleViews(
            UUID cursor,
            UUID inclusiveEnd,
            int limit,
            long version
    ) {
        return queryFactory
                .select(Projections.constructor(
                        ArticleViewRow.class,
                        articleView.id,
                        articleView.article.id,
                        articleView.user.id,
                        articleView.viewedAt
                ))
                .from(articleView)
                .where(
                        articleView.visibilityStatus.eq(ACTIVE),
                        articleView.article.deletedAt.isNull(),
                        articleView.user.deletedAt.isNull(),
                        after(articleView.id, cursor),
                        through(articleView.id, inclusiveEnd)
                )
                .orderBy(articleView.id.asc())
                .limit(limit)
                .fetch()
                .stream()
                .map(row -> new InitialProjectionEvent(
                        row.id(),
                        OutboxEventType.ARTICLE_VIEWED,
                        OutboxAggregateType.ARTICLE,
                        row.articleId(),
                        row.userId(),
                        new ArticleOutboxPayload(OutboxEventAction.VIEWED),
                        version,
                        row.occurredAt()
                ))
                .toList();
    }

    private BooleanExpression after(ComparableExpression<UUID> id, UUID cursor) {
        return cursor == null ? null : id.gt(cursor);
    }

    private BooleanExpression through(ComparableExpression<UUID> id, UUID inclusiveEnd) {
        return inclusiveEnd == null ? null : id.loe(inclusiveEnd);
    }

    public record SubscriptionRow(
            UUID id,
            UUID interestId,
            UUID userId,
            LocalDateTime occurredAt
    ) {
    }

    public record CommentRow(
            UUID id,
            UUID articleId,
            UUID userId,
            LocalDateTime occurredAt
    ) {
    }

    public record CommentLikeRow(
            UUID id,
            UUID commentId,
            UUID articleId,
            UUID userId,
            LocalDateTime occurredAt
    ) {
    }

    public record ArticleViewRow(
            UUID id,
            UUID articleId,
            UUID userId,
            LocalDateTime occurredAt
    ) {
    }
}
