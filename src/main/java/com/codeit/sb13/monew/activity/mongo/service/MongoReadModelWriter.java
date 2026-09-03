package com.codeit.sb13.monew.activity.mongo.service;

import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.ACTIVITY_HISTORIES;
import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.ARTICLE_SNAPSHOTS;
import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.COMMENT_SNAPSHOTS;
import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.INTEREST_SNAPSHOTS;

import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryDocument;
import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryStatus;
import com.codeit.sb13.monew.activity.mongo.document.ActivityTargetType;
import com.codeit.sb13.monew.activity.mongo.document.ArticleActivitySnapshot;
import com.codeit.sb13.monew.activity.mongo.document.CommentActivitySnapshot;
import com.codeit.sb13.monew.activity.mongo.document.InterestActivitySnapshot;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.ArticleState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.CommentState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.InterestState;
import com.mongodb.client.result.UpdateResult;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * 전역 projection version을 CAS 조건으로 사용해 MongoDB Read Model을 갱신한다.
 *
 * <p>같거나 더 최신 버전이 이미 저장된 경우 성공한 stale no-op으로 취급한다.
 * 모든 문서의 {@code _id}는 논리 키에서 결정적으로 계산하므로, 식별 필드를 지운
 * tombstone도 뒤늦게 도착한 과거 이벤트의 재삽입을 차단한다.</p>
 */
@Component
@RequiredArgsConstructor
public class MongoReadModelWriter {

    private final MongoTemplate mongoTemplate;

    public void upsertActivity(
            ActivityProjection activity,
            long projectionVersion,
            LocalDateTime now
    ) {
        String id = activityId(activity);
        Update update = versioned(new Update(), projectionVersion)
                .set("sourceActivityId", uuid(activity.sourceActivityId()))
                .set("userId", uuid(activity.userId()))
                .set("type", activity.type())
                .set("targetType", activity.targetType())
                .set("targetId", uuid(activity.targetId()))
                .set("visible", true)
                .set("tombstone", false)
                .set("status", ActivityHistoryStatus.ACTIVE)
                .unset("hiddenByTargetType")
                .unset("hiddenByTargetId")
                .set("updatedAt", now)
                .setOnInsert("createdAt", now)
                .max("occurredAt", activity.occurredAt());
        setParent(update, activity);
        casUpsert(id, projectionVersion, update, ActivityHistoryDocument.class,
                ACTIVITY_HISTORIES);
    }

    public void hideActivity(
            ActivityProjection activity,
            ActivityHistoryStatus status,
            ActivityTargetType hiddenByTargetType,
            UUID hiddenByTargetId,
            long projectionVersion,
            LocalDateTime now
    ) {
        String id = activityId(activity);
        Update update = versioned(new Update(), projectionVersion)
                .set("sourceActivityId", uuid(activity.sourceActivityId()))
                .set("userId", uuid(activity.userId()))
                .set("type", activity.type())
                .set("targetType", activity.targetType())
                .set("targetId", uuid(activity.targetId()))
                .set("visible", false)
                .set("tombstone", false)
                .set("status", status)
                .set("updatedAt", now)
                .setOnInsert("createdAt", now)
                .max("occurredAt", activity.occurredAt());
        setParent(update, activity);
        if (hiddenByTargetType == null) {
            update.unset("hiddenByTargetType").unset("hiddenByTargetId");
        } else {
            update.set("hiddenByTargetType", hiddenByTargetType)
                    .set("hiddenByTargetId", uuid(hiddenByTargetId));
        }
        if (updateExistingActivity(id, projectionVersion, true, update)) {
            return;
        }
        Update fenceOnly = versioned(new Update(), projectionVersion).set("updatedAt", now);
        if (updateExistingActivity(id, projectionVersion, false, fenceOnly)) {
            return;
        }
        casUpsert(id, projectionVersion, update, ActivityHistoryDocument.class,
                ACTIVITY_HISTORIES);
    }

    public void tombstoneActivity(
            ActivityProjection activity,
            long projectionVersion,
            LocalDateTime now
    ) {
        tombstone(
                activityId(activity), projectionVersion, now,
                ActivityHistoryDocument.class, ACTIVITY_HISTORIES,
                "sourceActivityId", "userId", "type", "targetType", "targetId",
                "parentTargetType", "parentTargetId", "occurredAt", "status",
                "hiddenByTargetType", "hiddenByTargetId", "createdAt"
        );
    }

    public void upsertCommentSnapshot(
            CommentState state,
            long projectionVersion,
            LocalDateTime now
    ) {
        String id = MongoProjectionKeyFactory.comment(state.id());
        Update update = versioned(new Update(), projectionVersion)
                .set("commentId", uuid(state.id()))
                .set("articleId", uuid(state.articleId()))
                .set("articleTitle", state.articleTitle())
                .set("authorUserId", uuid(state.authorUserId()))
                .set("authorNickname", state.authorNickname())
                .set("content", state.content())
                .set("likeCount", state.likeCount())
                .set("visible", true)
                .set("tombstone", false)
                .set("createdAt", state.createdAt())
                .set("updatedAt", state.updatedAt() == null ? now : state.updatedAt());
        casUpsert(id, projectionVersion, update, CommentActivitySnapshot.class,
                COMMENT_SNAPSHOTS);
    }

    public void upsertArticleSnapshot(
            ArticleState state,
            long projectionVersion,
            LocalDateTime now
    ) {
        String id = MongoProjectionKeyFactory.article(state.id());
        Update update = versioned(new Update(), projectionVersion)
                .set("articleId", uuid(state.id()))
                .set("title", state.title())
                .set("summary", state.summary())
                .set("source", state.source())
                .set("sourceUrl", state.sourceUrl())
                .set("publishedAt", state.publishedAt())
                .set("viewCount", state.viewCount())
                .set("commentCount", state.commentCount())
                .set("visible", true)
                .set("tombstone", false)
                .set("updatedAt", state.updatedAt() == null ? now : state.updatedAt());
        casUpsert(id, projectionVersion, update, ArticleActivitySnapshot.class,
                ARTICLE_SNAPSHOTS);
    }

    public void upsertInterestSnapshot(
            InterestState state,
            long projectionVersion,
            LocalDateTime now
    ) {
        String id = MongoProjectionKeyFactory.interest(state.id());
        Update update = versioned(new Update(), projectionVersion)
                .set("interestId", uuid(state.id()))
                .set("name", state.name())
                .set("keywords", state.keywords())
                .set("subscriberCount", state.subscriberCount())
                .set("visible", true)
                .set("tombstone", false)
                .set("updatedAt", state.updatedAt() == null ? now : state.updatedAt());
        casUpsert(id, projectionVersion, update, InterestActivitySnapshot.class,
                INTEREST_SNAPSHOTS);
    }

    public void hideCommentSnapshot(UUID id, long version, LocalDateTime now) {
        hideSnapshot(MongoProjectionKeyFactory.comment(id), "commentId", id, version, now,
                CommentActivitySnapshot.class, COMMENT_SNAPSHOTS);
    }

    public void hideArticleSnapshot(UUID id, long version, LocalDateTime now) {
        hideSnapshot(MongoProjectionKeyFactory.article(id), "articleId", id, version, now,
                ArticleActivitySnapshot.class, ARTICLE_SNAPSHOTS);
    }

    public void hideInterestSnapshot(UUID id, long version, LocalDateTime now) {
        hideSnapshot(MongoProjectionKeyFactory.interest(id), "interestId", id, version, now,
                InterestActivitySnapshot.class, INTEREST_SNAPSHOTS);
    }

    public void tombstoneComment(UUID id, long version, LocalDateTime now) {
        tombstone(MongoProjectionKeyFactory.comment(id), version, now,
                CommentActivitySnapshot.class, COMMENT_SNAPSHOTS,
                "commentId", "articleId", "articleTitle", "authorUserId", "authorNickname",
                "content", "likeCount", "createdAt");
    }

    public void tombstoneArticle(UUID id, long version, LocalDateTime now) {
        tombstone(MongoProjectionKeyFactory.article(id), version, now,
                ArticleActivitySnapshot.class, ARTICLE_SNAPSHOTS,
                "articleId", "title", "summary", "source", "sourceUrl", "publishedAt",
                "viewCount", "commentCount");
    }

    public void tombstoneInterest(UUID id, long version, LocalDateTime now) {
        tombstone(MongoProjectionKeyFactory.interest(id), version, now,
                InterestActivitySnapshot.class, INTEREST_SNAPSHOTS,
                "interestId", "name", "keywords", "subscriberCount");
    }

    public void hideActivitiesByUser(UUID userId, long version, LocalDateTime now) {
        bulkHideActivity(
                Criteria.where("userId").is(uuid(userId)),
                ActivityHistoryStatus.USER_DELETED,
                null,
                null,
                version,
                now
        );
    }

    public void hideActivitiesByTarget(
            ActivityTargetType targetType,
            UUID targetId,
            long version,
            LocalDateTime now
    ) {
        bulkHideActivity(
                Criteria.where("targetType").is(targetType).and("targetId").is(uuid(targetId)),
                ActivityHistoryStatus.TARGET_DELETED,
                targetType,
                targetId,
                version,
                now
        );
    }

    public void hideActivitiesByParent(
            ActivityTargetType parentType,
            UUID parentId,
            long version,
            LocalDateTime now
    ) {
        bulkHideActivity(
                Criteria.where("parentTargetType").is(parentType)
                        .and("parentTargetId").is(uuid(parentId)),
                ActivityHistoryStatus.TARGET_DELETED,
                parentType,
                parentId,
                version,
                now
        );
    }

    public void hideCommentSnapshotsByAuthor(UUID userId, long version, LocalDateTime now) {
        bulkHideSnapshot(
                Criteria.where("authorUserId").is(uuid(userId)),
                version,
                now,
                CommentActivitySnapshot.class,
                COMMENT_SNAPSHOTS
        );
    }

    public void hideCommentSnapshotsByArticle(UUID articleId, long version, LocalDateTime now) {
        bulkHideSnapshot(
                Criteria.where("articleId").is(uuid(articleId)),
                version,
                now,
                CommentActivitySnapshot.class,
                COMMENT_SNAPSHOTS
        );
    }

    public void tombstoneActivitiesByUser(UUID userId, long version, LocalDateTime now) {
        bulkTombstoneActivity(Criteria.where("userId").is(uuid(userId)), version, now);
    }

    public void tombstoneActivitiesByTarget(
            ActivityTargetType targetType,
            UUID targetId,
            long version,
            LocalDateTime now
    ) {
        bulkTombstoneActivity(
                Criteria.where("targetType").is(targetType).and("targetId").is(uuid(targetId)),
                version,
                now
        );
    }

    public void tombstoneActivitiesByParent(
            ActivityTargetType parentType,
            UUID parentId,
            long version,
            LocalDateTime now
    ) {
        bulkTombstoneActivity(
                Criteria.where("parentTargetType").is(parentType)
                        .and("parentTargetId").is(uuid(parentId)),
                version,
                now
        );
    }

    public void tombstoneCommentSnapshotsByAuthor(
            UUID userId,
            long version,
            LocalDateTime now
    ) {
        bulkTombstoneCommentSnapshots(
                Criteria.where("authorUserId").is(uuid(userId)), version, now);
    }

    public void tombstoneCommentSnapshotsByArticle(
            UUID articleId,
            long version,
            LocalDateTime now
    ) {
        bulkTombstoneCommentSnapshots(
                Criteria.where("articleId").is(uuid(articleId)), version, now);
    }

    private <T> void hideSnapshot(
            String documentId,
            String naturalIdField,
            UUID naturalId,
            long version,
            LocalDateTime now,
            Class<T> type,
            String collection
    ) {
        Update update = versioned(new Update(), version)
                .set(naturalIdField, uuid(naturalId))
                .set("visible", false)
                .set("tombstone", false)
                .set("updatedAt", now);
        casUpsert(documentId, version, update, type, collection);
    }

    private void bulkHideActivity(
            Criteria target,
            ActivityHistoryStatus status,
            ActivityTargetType hiddenByType,
            UUID hiddenById,
            long version,
            LocalDateTime now
    ) {
        Update update = versioned(new Update(), version)
                .set("visible", false)
                .set("tombstone", false)
                .set("status", status)
                .set("updatedAt", now);
        if (hiddenByType == null) {
            update.unset("hiddenByTargetType").unset("hiddenByTargetId");
        } else {
            update.set("hiddenByTargetType", hiddenByType)
                    .set("hiddenByTargetId", uuid(hiddenById));
        }
        mongoTemplate.updateMulti(
                staleQuery(new Criteria().andOperator(
                        target, Criteria.where("visible").is(true)), version), update,
                ActivityHistoryDocument.class, ACTIVITY_HISTORIES);
        Update fenceOnly = versioned(new Update(), version).set("updatedAt", now);
        mongoTemplate.updateMulti(
                staleQuery(new Criteria().andOperator(
                        target, Criteria.where("visible").is(false)), version), fenceOnly,
                ActivityHistoryDocument.class, ACTIVITY_HISTORIES);
    }

    private <T> void bulkHideSnapshot(
            Criteria target,
            long version,
            LocalDateTime now,
            Class<T> type,
            String collection
    ) {
        Update update = versioned(new Update(), version)
                .set("visible", false)
                .set("tombstone", false)
                .set("updatedAt", now);
        mongoTemplate.updateMulti(staleQuery(target, version), update, type, collection);
    }

    private void bulkTombstoneActivity(Criteria target, long version, LocalDateTime now) {
        Update update = tombstoneUpdate(version, now,
                "sourceActivityId", "userId", "type", "targetType", "targetId",
                "parentTargetType", "parentTargetId", "occurredAt", "status",
                "hiddenByTargetType", "hiddenByTargetId", "createdAt");
        mongoTemplate.updateMulti(
                staleQuery(target, version), update,
                ActivityHistoryDocument.class, ACTIVITY_HISTORIES);
    }

    private void bulkTombstoneCommentSnapshots(
            Criteria target,
            long version,
            LocalDateTime now
    ) {
        Update update = tombstoneUpdate(version, now,
                "commentId", "articleId", "articleTitle", "authorUserId", "authorNickname",
                "content", "likeCount", "createdAt");
        mongoTemplate.updateMulti(
                staleQuery(target, version), update,
                CommentActivitySnapshot.class, COMMENT_SNAPSHOTS);
    }

    private <T> void tombstone(
            String id,
            long version,
            LocalDateTime now,
            Class<T> type,
            String collection,
            String... fieldsToUnset
    ) {
        Update update = tombstoneUpdate(version, now, fieldsToUnset);
        casUpsert(id, version, update, type, collection);
    }

    private Update tombstoneUpdate(
            long version,
            LocalDateTime now,
            String... fieldsToUnset
    ) {
        Update update = versioned(new Update(), version)
                .set("tombstone", true)
                .set("visible", false)
                .set("updatedAt", now);
        for (String field : fieldsToUnset) {
            update.unset(field);
        }
        return update;
    }

    private <T> void casUpsert(
            String id,
            long version,
            Update update,
            Class<T> type,
            String collection
    ) {
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(id),
                new Criteria().orOperator(
                        Criteria.where("projectionVersion").exists(false),
                        Criteria.where("projectionVersion").lt(version)
                )
        ));
        try {
            mongoTemplate.upsert(query, update, type, collection);
        } catch (DuplicateKeyException e) {
            Query newerOrEqual = Query.query(Criteria.where("_id").is(id)
                    .and("projectionVersion").gte(version));
            if (!mongoTemplate.exists(newerOrEqual, type, collection)) {
                throw e;
            }
        }
    }

    private boolean updateExistingActivity(
            String id,
            long version,
            boolean visible,
            Update update
    ) {
        Query query = staleQuery(new Criteria().andOperator(
                Criteria.where("_id").is(id), Criteria.where("visible").is(visible)), version);
        UpdateResult result = mongoTemplate.updateFirst(
                query, update, ActivityHistoryDocument.class, ACTIVITY_HISTORIES);
        return result != null && result.getMatchedCount() > 0;
    }

    private Query staleQuery(Criteria target, long version) {
        return Query.query(new Criteria().andOperator(target, staleVersion(version)));
    }

    private Criteria staleVersion(long version) {
        return new Criteria().orOperator(
                Criteria.where("projectionVersion").exists(false),
                Criteria.where("projectionVersion").lt(version)
        );
    }

    private Update versioned(Update update, long projectionVersion) {
        return update.set("projectionVersion", projectionVersion);
    }

    private void setParent(Update update, ActivityProjection activity) {
        if (activity.parentTargetType() == null) {
            update.unset("parentTargetType").unset("parentTargetId");
        } else {
            update.set("parentTargetType", activity.parentTargetType())
                    .set("parentTargetId", uuid(activity.parentTargetId()));
        }
    }

    private String activityId(ActivityProjection activity) {
        return MongoProjectionKeyFactory.activity(
                activity.userId(), activity.type(), activity.targetType(), activity.targetId());
    }

    private String uuid(UUID id) {
        return id == null ? null : id.toString();
    }
}
