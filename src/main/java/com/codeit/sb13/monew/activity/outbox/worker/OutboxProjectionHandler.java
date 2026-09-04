package com.codeit.sb13.monew.activity.outbox.worker;

import static com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryStatus.CANCELED;
import static com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryStatus.TARGET_DELETED;
import static com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryStatus.UNSUBSCRIBED;
import static com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryStatus.USER_DELETED;

import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryType;
import com.codeit.sb13.monew.activity.mongo.document.ActivityTargetType;
import com.codeit.sb13.monew.activity.mongo.service.ActivityProjection;
import com.codeit.sb13.monew.activity.mongo.service.MongoReadModelWriter;
import com.codeit.sb13.monew.activity.outbox.payload.ActivityProjectionKeyPayload;
import com.codeit.sb13.monew.activity.outbox.payload.ArticleOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.payload.CommentOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.payload.InterestOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.payload.ProjectionImpact;
import com.codeit.sb13.monew.activity.outbox.payload.UserHardDeleteOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.payload.UserOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.ArticleState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.CommentState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.InterestState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.RelationKey;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.RelationState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.UserState;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * decode된 이벤트와 RDB 현재 상태를 versioned MongoDB Read Model 변경으로 해석한다.
 *
 * <p>모든 쓰기에 Outbox 전역 projection version을 전달한다. 삭제 payload가 가진
 * 영향 키는 문서가 아직 없더라도 hidden 또는 scrubbed tombstone으로 물질화되므로,
 * 다른 worker에서 뒤늦게 도착한 과거 이벤트가 대상을 되살릴 수 없다.</p>
 */
@Component
@RequiredArgsConstructor
public class OutboxProjectionHandler {

    private final MongoReadModelWriter writer;

    public void project(
            ProjectionCommand event,
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
                    refreshInterest(event.aggregateId(), event.projectionVersion(), source, now);
            case INTEREST_HARD_DELETED -> hardDeleteInterest(event, now);
            case COMMENT_UPDATED, COMMENT_LIKE_CHANGED ->
                    refreshComment(event.aggregateId(), event.projectionVersion(), source, now);
            case COMMENT_SOFT_DELETED -> softDeleteComment(event, now);
            case COMMENT_HARD_DELETED -> hardDeleteComment(event, now);
            case ARTICLE_VIEW_COUNT_CHANGED, ARTICLE_COMMENT_COUNT_CHANGED ->
                    refreshArticle(event.aggregateId(), event.projectionVersion(), source, now);
            case ARTICLE_SOFT_DELETED -> softDeleteArticle(event, now);
            case ARTICLE_HARD_DELETED -> hardDeleteArticle(event, now);
            case USER_NICKNAME_UPDATED -> refreshUserNickname(event, source, now);
            case USER_SOFT_DELETED -> softDeleteUser(event, now);
            case USER_HARD_DELETED -> hardDeleteUser(event, source, now);
        }
    }

    private void projectInterestActivity(
            ProjectionCommand event,
            ProjectionSourceBatch source,
            LocalDateTime now
    ) {
        ActivityProjection activity = activity(
                event,
                source.subscriptions().get(relationKey(event)),
                ActivityHistoryType.INTEREST_SUBSCRIBED,
                ActivityTargetType.INTEREST,
                null,
                null
        );
        InterestState interest = source.interests().get(event.aggregateId());
        if (interest == null) {
            writer.tombstoneInterest(event.aggregateId(), event.projectionVersion(), now);
            writer.tombstoneActivity(activity, event.projectionVersion(), now);
            return;
        }
        writer.upsertInterestSnapshot(interest, event.projectionVersion(), now);
        if (!actorActive(event, source)) {
            writer.hideActivity(activity, USER_DELETED, null, null,
                    event.projectionVersion(), now);
            return;
        }

        RelationState relation = source.subscriptions().get(relationKey(event));
        if (relation != null && relation.active()) {
            writer.upsertActivity(activity, event.projectionVersion(), now);
        } else {
            writer.hideActivity(activity, UNSUBSCRIBED, null, null,
                    event.projectionVersion(), now);
        }
    }

    private void projectWrittenComment(
            ProjectionCommand event,
            ProjectionSourceBatch source,
            LocalDateTime now
    ) {
        ActivityProjection activity = new ActivityProjection(
                event.aggregateId(), event.actorUserId(), ActivityHistoryType.COMMENT_WRITTEN,
                ActivityTargetType.COMMENT, event.aggregateId(), null, null, event.occurredAt());
        CommentState comment = source.comments().get(event.aggregateId());
        if (comment == null) {
            writer.tombstoneComment(event.aggregateId(), event.projectionVersion(), now);
            writer.tombstoneActivity(activity, event.projectionVersion(), now);
            return;
        }
        if (!comment.visible()) {
            writer.hideCommentSnapshot(comment.id(), event.projectionVersion(), now);
            writer.hideActivity(activity, TARGET_DELETED, ActivityTargetType.COMMENT,
                    comment.id(), event.projectionVersion(), now);
            return;
        }
        writer.upsertCommentSnapshot(comment, event.projectionVersion(), now);
        ActivityProjection populated = new ActivityProjection(
                comment.id(), event.actorUserId(), ActivityHistoryType.COMMENT_WRITTEN,
                ActivityTargetType.COMMENT, comment.id(), ActivityTargetType.ARTICLE,
                comment.articleId(), comment.createdAt());
        if (!actorActive(event, source)) {
            writer.hideActivity(populated, USER_DELETED, null, null,
                    event.projectionVersion(), now);
            return;
        }
        writer.upsertActivity(populated, event.projectionVersion(), now);
    }

    private void projectCommentLike(
            ProjectionCommand event,
            ProjectionSourceBatch source,
            LocalDateTime now
    ) {
        RelationState relation = source.commentLikes().get(relationKey(event));
        ActivityProjection activity = activity(
                event, relation, ActivityHistoryType.COMMENT_LIKED,
                ActivityTargetType.COMMENT, null, null);
        CommentState comment = source.comments().get(event.aggregateId());
        if (comment == null) {
            writer.tombstoneComment(event.aggregateId(), event.projectionVersion(), now);
            writer.tombstoneActivity(activity, event.projectionVersion(), now);
            return;
        }
        if (!comment.visible()) {
            writer.hideCommentSnapshot(comment.id(), event.projectionVersion(), now);
            writer.hideActivity(activity, TARGET_DELETED, ActivityTargetType.COMMENT,
                    comment.id(), event.projectionVersion(), now);
            return;
        }
        writer.upsertCommentSnapshot(comment, event.projectionVersion(), now);
        ActivityProjection populated = activity(
                event, relation, ActivityHistoryType.COMMENT_LIKED,
                ActivityTargetType.COMMENT, ActivityTargetType.ARTICLE, comment.articleId());
        if (!actorActive(event, source)) {
            writer.hideActivity(populated, USER_DELETED, null, null,
                    event.projectionVersion(), now);
            return;
        }
        if (relation != null && relation.active()) {
            writer.upsertActivity(populated, event.projectionVersion(), now);
        } else {
            writer.hideActivity(populated, CANCELED, null, null,
                    event.projectionVersion(), now);
        }
    }

    private void projectArticleView(
            ProjectionCommand event,
            ProjectionSourceBatch source,
            LocalDateTime now
    ) {
        RelationState relation = source.articleViews().get(relationKey(event));
        ActivityProjection activity = activity(
                event, relation, ActivityHistoryType.ARTICLE_VIEWED,
                ActivityTargetType.ARTICLE, null, null);
        ArticleState article = source.articles().get(event.aggregateId());
        if (article == null) {
            writer.tombstoneArticle(event.aggregateId(), event.projectionVersion(), now);
            writer.tombstoneActivity(activity, event.projectionVersion(), now);
            return;
        }
        if (!article.visible()) {
            writer.hideArticleSnapshot(article.id(), event.projectionVersion(), now);
            writer.hideActivity(activity, TARGET_DELETED, ActivityTargetType.ARTICLE,
                    article.id(), event.projectionVersion(), now);
            return;
        }
        writer.upsertArticleSnapshot(article, event.projectionVersion(), now);
        if (!actorActive(event, source)) {
            writer.hideActivity(activity, USER_DELETED, null, null,
                    event.projectionVersion(), now);
            return;
        }
        if (relation != null && relation.active()) {
            writer.upsertActivity(activity, event.projectionVersion(), now);
        }
    }

    private void refreshInterest(
            UUID interestId,
            long version,
            ProjectionSourceBatch source,
            LocalDateTime now
    ) {
        InterestState state = source.interests().get(interestId);
        if (state == null) {
            writer.tombstoneInterest(interestId, version, now);
        } else {
            writer.upsertInterestSnapshot(state, version, now);
        }
    }

    private void refreshComment(
            UUID commentId,
            long version,
            ProjectionSourceBatch source,
            LocalDateTime now
    ) {
        CommentState state = source.comments().get(commentId);
        if (state == null) {
            writer.tombstoneComment(commentId, version, now);
        } else if (state.visible()) {
            writer.upsertCommentSnapshot(state, version, now);
        } else {
            writer.hideCommentSnapshot(commentId, version, now);
        }
    }

    private void refreshArticle(
            UUID articleId,
            long version,
            ProjectionSourceBatch source,
            LocalDateTime now
    ) {
        ArticleState state = source.articles().get(articleId);
        if (state == null) {
            writer.tombstoneArticle(articleId, version, now);
        } else if (state.visible()) {
            writer.upsertArticleSnapshot(state, version, now);
        } else {
            writer.hideArticleSnapshot(articleId, version, now);
        }
    }

    private void softDeleteComment(ProjectionCommand event, LocalDateTime now) {
        writer.hideCommentSnapshot(event.aggregateId(), event.projectionVersion(), now);
        writer.hideActivitiesByTarget(
                ActivityTargetType.COMMENT, event.aggregateId(), event.projectionVersion(), now);
        hideImpact(impactOf(event), TARGET_DELETED, ActivityTargetType.COMMENT,
                event.aggregateId(), event, now);
    }

    private void hardDeleteComment(ProjectionCommand event, LocalDateTime now) {
        writer.tombstoneComment(event.aggregateId(), event.projectionVersion(), now);
        writer.tombstoneActivitiesByTarget(
                ActivityTargetType.COMMENT, event.aggregateId(), event.projectionVersion(), now);
        tombstoneImpact(impactOf(event), event, now);
    }

    private void softDeleteArticle(ProjectionCommand event, LocalDateTime now) {
        ProjectionImpact impact = impactOf(event);
        writer.hideArticleSnapshot(event.aggregateId(), event.projectionVersion(), now);
        writer.hideCommentSnapshotsByArticle(
                event.aggregateId(), event.projectionVersion(), now);
        writer.hideActivitiesByTarget(
                ActivityTargetType.ARTICLE, event.aggregateId(), event.projectionVersion(), now);
        writer.hideActivitiesByParent(
                ActivityTargetType.ARTICLE, event.aggregateId(), event.projectionVersion(), now);
        impact.commentSnapshotIds().forEach(id ->
                writer.hideCommentSnapshot(id, event.projectionVersion(), now));
        hideImpact(impact, TARGET_DELETED, ActivityTargetType.ARTICLE,
                event.aggregateId(), event, now);
    }

    private void hardDeleteArticle(ProjectionCommand event, LocalDateTime now) {
        ProjectionImpact impact = impactOf(event);
        writer.tombstoneArticle(event.aggregateId(), event.projectionVersion(), now);
        writer.tombstoneCommentSnapshotsByArticle(
                event.aggregateId(), event.projectionVersion(), now);
        writer.tombstoneActivitiesByTarget(
                ActivityTargetType.ARTICLE, event.aggregateId(), event.projectionVersion(), now);
        writer.tombstoneActivitiesByParent(
                ActivityTargetType.ARTICLE, event.aggregateId(), event.projectionVersion(), now);
        impact.commentSnapshotIds().forEach(id ->
                writer.tombstoneComment(id, event.projectionVersion(), now));
        tombstoneImpact(impact, event, now);
    }

    private void hardDeleteInterest(ProjectionCommand event, LocalDateTime now) {
        writer.tombstoneInterest(event.aggregateId(), event.projectionVersion(), now);
        writer.tombstoneActivitiesByTarget(
                ActivityTargetType.INTEREST, event.aggregateId(), event.projectionVersion(), now);
        tombstoneImpact(impactOf(event), event, now);
    }

    private void refreshUserNickname(
            ProjectionCommand event,
            ProjectionSourceBatch source,
            LocalDateTime now
    ) {
        UserState user = source.users().get(event.aggregateId());
        if (user == null || !user.active()) {
            softDeleteUser(event, now);
            return;
        }
        impactOf(event).commentSnapshotIds().forEach(id ->
                refreshComment(id, event.projectionVersion(), source, now));
    }

    private void softDeleteUser(ProjectionCommand event, LocalDateTime now) {
        ProjectionImpact impact = impactOf(event);
        writer.hideActivitiesByUser(event.aggregateId(), event.projectionVersion(), now);
        writer.hideCommentSnapshotsByAuthor(
                event.aggregateId(), event.projectionVersion(), now);
        hideImpact(impact, USER_DELETED, null, null, event, now);
        impact.commentSnapshotIds().forEach(id ->
                writer.hideCommentSnapshot(id, event.projectionVersion(), now));
    }

    private void hardDeleteUser(
            ProjectionCommand event,
            ProjectionSourceBatch source,
            LocalDateTime now
    ) {
        UserHardDeleteOutboxPayload payload = (UserHardDeleteOutboxPayload) event.payload();
        writer.tombstoneActivitiesByUser(
                event.aggregateId(), event.projectionVersion(), now);
        writer.tombstoneCommentSnapshotsByAuthor(
                event.aggregateId(), event.projectionVersion(), now);
        tombstoneImpact(payload.impact(), event, now);
        payload.impact().commentSnapshotIds().forEach(id ->
                writer.tombstoneComment(id, event.projectionVersion(), now));

        new LinkedHashSet<>(payload.likedCommentIds()).forEach(id ->
                refreshComment(id, event.projectionVersion(), source, now));
        LinkedHashSet<UUID> articleIds = new LinkedHashSet<>(payload.impactedArticleIds());
        articleIds.addAll(payload.viewedArticleIds());
        articleIds.forEach(id ->
                refreshArticle(id, event.projectionVersion(), source, now));
        new LinkedHashSet<>(payload.subscribedInterestIds()).forEach(id ->
                refreshInterest(id, event.projectionVersion(), source, now));
    }

    private void hideImpact(
            ProjectionImpact impact,
            com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryStatus status,
            ActivityTargetType hiddenByType,
            UUID hiddenById,
            ProjectionCommand event,
            LocalDateTime now
    ) {
        impact.activityKeys().forEach(key -> writer.hideActivity(
                activity(key, event.occurredAt()), status, hiddenByType, hiddenById,
                event.projectionVersion(), now));
    }

    private void tombstoneImpact(
            ProjectionImpact impact,
            ProjectionCommand event,
            LocalDateTime now
    ) {
        impact.activityKeys().forEach(key -> writer.tombstoneActivity(
                activity(key, event.occurredAt()), event.projectionVersion(), now));
    }

    private ProjectionImpact impactOf(ProjectionCommand event) {
        if (event.payload() instanceof ArticleOutboxPayload value) {
            return value.impact();
        }
        if (event.payload() instanceof CommentOutboxPayload value) {
            return value.impact();
        }
        if (event.payload() instanceof InterestOutboxPayload value) {
            return value.impact();
        }
        if (event.payload() instanceof UserOutboxPayload value) {
            return value.impact();
        }
        if (event.payload() instanceof UserHardDeleteOutboxPayload value) {
            return value.impact();
        }
        return ProjectionImpact.EMPTY;
    }

    private boolean actorActive(ProjectionCommand event, ProjectionSourceBatch source) {
        UserState actor = source.users().get(event.actorUserId());
        return actor != null && actor.active();
    }

    private RelationKey relationKey(ProjectionCommand event) {
        return new RelationKey(event.aggregateId(), event.actorUserId());
    }

    private ActivityProjection activity(
            ProjectionCommand event,
            RelationState relation,
            ActivityHistoryType type,
            ActivityTargetType targetType,
            ActivityTargetType parentTargetType,
            UUID parentTargetId
    ) {
        return new ActivityProjection(
                relation == null ? null : relation.id(), event.actorUserId(), type,
                targetType, event.aggregateId(), parentTargetType, parentTargetId,
                relation == null ? event.occurredAt() : relation.occurredAt());
    }

    private ActivityProjection activity(
            ActivityProjectionKeyPayload key,
            LocalDateTime occurredAt
    ) {
        ActivityHistoryType type;
        ActivityTargetType targetType;
        switch (key.activityEventType()) {
            case INTEREST_SUBSCRIBED -> {
                type = ActivityHistoryType.INTEREST_SUBSCRIBED;
                targetType = ActivityTargetType.INTEREST;
            }
            case COMMENT_WRITTEN -> {
                type = ActivityHistoryType.COMMENT_WRITTEN;
                targetType = ActivityTargetType.COMMENT;
            }
            case COMMENT_LIKED -> {
                type = ActivityHistoryType.COMMENT_LIKED;
                targetType = ActivityTargetType.COMMENT;
            }
            case ARTICLE_VIEWED -> {
                type = ActivityHistoryType.ARTICLE_VIEWED;
                targetType = ActivityTargetType.ARTICLE;
            }
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 activity key type: " + key.activityEventType());
        }
        return new ActivityProjection(
                null, key.userId(), type, targetType, key.targetId(),
                null, null, occurredAt);
    }
}
