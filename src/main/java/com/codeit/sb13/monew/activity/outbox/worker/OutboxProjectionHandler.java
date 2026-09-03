package com.codeit.sb13.monew.activity.outbox.worker;

import static com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryStatus.CANCELED;
import static com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryStatus.UNSUBSCRIBED;

import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryType;
import com.codeit.sb13.monew.activity.mongo.document.ActivityTargetType;
import com.codeit.sb13.monew.activity.mongo.service.ActivityProjection;
import com.codeit.sb13.monew.activity.mongo.service.MongoReadModelWriter;
import com.codeit.sb13.monew.activity.outbox.payload.UserHardDeleteOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.ArticleState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.CommentState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.InterestState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.RelationKey;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.RelationState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.UserState;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * decode된 이벤트와 RDB 현재 상태를 MongoDB Read Model 변경으로 해석한다.
 *
 * <p>이벤트 payload의 과거 표시값이 아니라 {@link ProjectionSourceBatch}에 담긴
 * 현재 원본·관계 상태를 사용한다. 원본이 없으면 문서를 정리하고, 논리삭제 상태면
 * 숨기며, 활성 상태면 snapshot과 activity를 멱등 upsert한다.</p>
 */
@Component
@RequiredArgsConstructor
public class OutboxProjectionHandler {

    private final MongoReadModelWriter writer;

    /**
     * 이벤트 종류에 해당하는 MongoDB projection 규칙을 실행한다.
     *
     * @param event 처리할 타입 지정 이벤트
     * @param source 같은 claim batch를 위해 조회한 RDB 현재 상태
     * @param now MongoDB 문서의 반영 시각
     */
    public void project(
            DecodedOutboxEvent event,
            ProjectionSourceBatch source,
            LocalDateTime now
    ) {
        switch (event.eventType()) {
            case INTEREST_SUBSCRIBED, INTEREST_UNSUBSCRIBED ->
                    projectInterestActivity(event, source, now);
            case COMMENT_WRITTEN -> projectWrittenComment(event, source, now);
            case COMMENT_LIKED, COMMENT_LIKE_CANCELED ->
                    projectCommentLike(event, source, now);
            case ARTICLE_VIEWED -> projectArticleView(event, source, now);
            case INTEREST_UPDATED, INTEREST_SUBSCRIBER_COUNT_CHANGED ->
                    refreshInterest(event.aggregateId(), source, now);
            case INTEREST_HARD_DELETED -> writer.deleteInterest(event.aggregateId());
            case COMMENT_UPDATED, COMMENT_LIKE_CHANGED ->
                    refreshComment(event.aggregateId(), source, now);
            case COMMENT_SOFT_DELETED -> refreshOrHideComment(event.aggregateId(), source, now);
            case COMMENT_HARD_DELETED -> writer.deleteComment(event.aggregateId());
            case ARTICLE_VIEW_COUNT_CHANGED, ARTICLE_COMMENT_COUNT_CHANGED ->
                    refreshArticle(event.aggregateId(), source, now);
            case ARTICLE_SOFT_DELETED -> refreshOrHideArticle(event.aggregateId(), source, now);
            case ARTICLE_HARD_DELETED -> writer.deleteArticle(event.aggregateId());
            case USER_NICKNAME_UPDATED -> refreshUserNickname(event.aggregateId(), source, now);
            case USER_SOFT_DELETED -> hideUser(event.aggregateId(), now);
            case USER_HARD_DELETED -> cleanupUser(event, source, now);
        }
    }

    private void projectInterestActivity(
            DecodedOutboxEvent event,
            ProjectionSourceBatch source,
            LocalDateTime now
    ) {
        InterestState interest = source.interests().get(event.aggregateId());
        if (interest == null) {
            writer.deleteInterest(event.aggregateId());
            return;
        }
        writer.upsertInterestSnapshot(interest, now);
        if (!actorActive(event, source)) {
            hideDeletedActor(event, now);
            return;
        }

        RelationState relation = source.subscriptions().get(relationKey(event));
        ActivityProjection activity = activity(
                event,
                relation,
                ActivityHistoryType.INTEREST_SUBSCRIBED,
                ActivityTargetType.INTEREST,
                null,
                null
        );
        if (relation != null && relation.active()) {
            writer.upsertActivity(activity, now);
        } else {
            writer.hideActivity(activity, UNSUBSCRIBED, null, null, now);
        }
    }

    private void projectWrittenComment(
            DecodedOutboxEvent event,
            ProjectionSourceBatch source,
            LocalDateTime now
    ) {
        CommentState comment = source.comments().get(event.aggregateId());
        if (comment == null) {
            writer.deleteComment(event.aggregateId());
            return;
        }
        if (!comment.visible()) {
            writer.hideComment(comment.id(), now);
            return;
        }
        writer.upsertCommentSnapshot(comment, now);
        if (!actorActive(event, source)) {
            hideDeletedActor(event, now);
            return;
        }
        writer.upsertActivity(new ActivityProjection(
                comment.id(),
                event.actorUserId(),
                ActivityHistoryType.COMMENT_WRITTEN,
                ActivityTargetType.COMMENT,
                comment.id(),
                ActivityTargetType.ARTICLE,
                comment.articleId(),
                comment.createdAt()
        ), now);
    }

    private void projectCommentLike(
            DecodedOutboxEvent event,
            ProjectionSourceBatch source,
            LocalDateTime now
    ) {
        CommentState comment = source.comments().get(event.aggregateId());
        if (comment == null) {
            writer.deleteComment(event.aggregateId());
            return;
        }
        if (!comment.visible()) {
            writer.hideComment(comment.id(), now);
            return;
        }
        writer.upsertCommentSnapshot(comment, now);
        if (!actorActive(event, source)) {
            hideDeletedActor(event, now);
            return;
        }

        RelationState relation = source.commentLikes().get(relationKey(event));
        ActivityProjection activity = activity(
                event,
                relation,
                ActivityHistoryType.COMMENT_LIKED,
                ActivityTargetType.COMMENT,
                ActivityTargetType.ARTICLE,
                comment.articleId()
        );
        if (relation != null && relation.active()) {
            writer.upsertActivity(activity, now);
        } else {
            writer.hideActivity(activity, CANCELED, null, null, now);
        }
    }

    private void projectArticleView(
            DecodedOutboxEvent event,
            ProjectionSourceBatch source,
            LocalDateTime now
    ) {
        ArticleState article = source.articles().get(event.aggregateId());
        if (article == null) {
            writer.deleteArticle(event.aggregateId());
            return;
        }
        if (!article.visible()) {
            writer.hideArticle(article.id(), now);
            return;
        }
        writer.upsertArticleSnapshot(article, now);
        if (!actorActive(event, source)) {
            hideDeletedActor(event, now);
            return;
        }

        RelationState relation = source.articleViews().get(relationKey(event));
        if (relation == null || !relation.active()) {
            return;
        }
        writer.upsertActivity(activity(
                event,
                relation,
                ActivityHistoryType.ARTICLE_VIEWED,
                ActivityTargetType.ARTICLE,
                null,
                null
        ), now);
    }

    private void refreshInterest(UUID interestId, ProjectionSourceBatch source, LocalDateTime now) {
        InterestState interest = source.interests().get(interestId);
        if (interest == null) {
            writer.deleteInterest(interestId);
        } else {
            writer.upsertInterestSnapshot(interest, now);
        }
    }

    private void refreshComment(UUID commentId, ProjectionSourceBatch source, LocalDateTime now) {
        CommentState comment = source.comments().get(commentId);
        if (comment == null) {
            writer.deleteComment(commentId);
        } else if (comment.visible()) {
            writer.upsertCommentSnapshot(comment, now);
        } else {
            writer.hideComment(commentId, now);
        }
    }

    private void refreshOrHideComment(
            UUID commentId,
            ProjectionSourceBatch source,
            LocalDateTime now
    ) {
        refreshComment(commentId, source, now);
    }

    private void refreshArticle(UUID articleId, ProjectionSourceBatch source, LocalDateTime now) {
        ArticleState article = source.articles().get(articleId);
        if (article == null) {
            writer.deleteArticle(articleId);
        } else if (article.visible()) {
            writer.upsertArticleSnapshot(article, now);
        } else {
            writer.hideArticle(articleId, now);
        }
    }

    private void refreshOrHideArticle(
            UUID articleId,
            ProjectionSourceBatch source,
            LocalDateTime now
    ) {
        refreshArticle(articleId, source, now);
    }

    private void refreshUserNickname(
            UUID userId,
            ProjectionSourceBatch source,
            LocalDateTime now
    ) {
        UserState user = source.users().get(userId);
        if (user == null || !user.active()) {
            hideUser(userId, now);
            return;
        }
        writer.updateCommentAuthorNickname(userId, user.nickname(), now);
    }

    private void hideUser(UUID userId, LocalDateTime now) {
        writer.hideActivitiesByUser(userId, now);
        writer.hideCommentSnapshotsByAuthor(userId, now);
    }

    private void cleanupUser(
            DecodedOutboxEvent event,
            ProjectionSourceBatch source,
            LocalDateTime now
    ) {
        writer.deleteUser(event.aggregateId());
        UserHardDeleteOutboxPayload payload = (UserHardDeleteOutboxPayload) event.payload();
        writer.deleteComments(payload.authoredCommentIds());

        Set<UUID> commentIds = new LinkedHashSet<>(payload.likedCommentIds());
        commentIds.forEach(id -> refreshComment(id, source, now));

        Set<UUID> articleIds = new LinkedHashSet<>(payload.impactedArticleIds());
        articleIds.addAll(payload.viewedArticleIds());
        articleIds.forEach(id -> refreshArticle(id, source, now));

        new LinkedHashSet<>(payload.subscribedInterestIds())
                .forEach(id -> refreshInterest(id, source, now));
    }

    private boolean actorActive(DecodedOutboxEvent event, ProjectionSourceBatch source) {
        UserState actor = source.users().get(event.actorUserId());
        return actor != null && actor.active();
    }

    private void hideDeletedActor(DecodedOutboxEvent event, LocalDateTime now) {
        if (event.actorUserId() != null) {
            writer.hideActivitiesByUser(event.actorUserId(), now);
            writer.hideCommentSnapshotsByAuthor(event.actorUserId(), now);
        }
    }

    private RelationKey relationKey(DecodedOutboxEvent event) {
        return new RelationKey(event.aggregateId(), event.actorUserId());
    }

    private ActivityProjection activity(
            DecodedOutboxEvent event,
            RelationState relation,
            ActivityHistoryType type,
            ActivityTargetType targetType,
            ActivityTargetType parentTargetType,
            UUID parentTargetId
    ) {
        return new ActivityProjection(
                relation == null ? null : relation.id(),
                event.actorUserId(),
                type,
                targetType,
                event.aggregateId(),
                parentTargetType,
                parentTargetId,
                relation == null ? event.occurredAt() : relation.occurredAt()
        );
    }
}
