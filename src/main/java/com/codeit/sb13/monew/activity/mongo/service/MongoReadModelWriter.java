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
import com.codeit.sb13.monew.activity.mongo.document.QActivityHistoryDocument;
import com.codeit.sb13.monew.activity.mongo.document.QArticleActivitySnapshot;
import com.codeit.sb13.monew.activity.mongo.document.QCommentActivitySnapshot;
import com.codeit.sb13.monew.activity.mongo.document.QInterestActivitySnapshot;
import com.codeit.sb13.monew.activity.mongo.querydsl.MongoQuerydslSupport;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.ArticleState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.CommentState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.InterestState;
import com.mongodb.client.result.UpdateResult;
import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.StringPath;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
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

    private static final QActivityHistoryDocument ACTIVITY =
            QActivityHistoryDocument.activityHistoryDocument;
    private static final QCommentActivitySnapshot COMMENT_SNAPSHOT =
            QCommentActivitySnapshot.commentActivitySnapshot;
    private static final QArticleActivitySnapshot ARTICLE_SNAPSHOT =
            QArticleActivitySnapshot.articleActivitySnapshot;
    private static final QInterestActivitySnapshot INTEREST_SNAPSHOT =
            QInterestActivitySnapshot.interestActivitySnapshot;
    private static final VersionedDocument<ActivityHistoryDocument> ACTIVITY_DOCUMENT =
            new VersionedDocument<>(ACTIVITY, ACTIVITY.id, ACTIVITY.projectionVersion,
                    ActivityHistoryDocument.class, ACTIVITY_HISTORIES);
    private static final VersionedDocument<CommentActivitySnapshot> COMMENT_DOCUMENT =
            new VersionedDocument<>(COMMENT_SNAPSHOT, COMMENT_SNAPSHOT.id,
                    COMMENT_SNAPSHOT.projectionVersion,
                    CommentActivitySnapshot.class, COMMENT_SNAPSHOTS);
    private static final VersionedDocument<ArticleActivitySnapshot> ARTICLE_DOCUMENT =
            new VersionedDocument<>(ARTICLE_SNAPSHOT, ARTICLE_SNAPSHOT.id,
                    ARTICLE_SNAPSHOT.projectionVersion,
                    ArticleActivitySnapshot.class, ARTICLE_SNAPSHOTS);
    private static final VersionedDocument<InterestActivitySnapshot> INTEREST_DOCUMENT =
            new VersionedDocument<>(INTEREST_SNAPSHOT, INTEREST_SNAPSHOT.id,
                    INTEREST_SNAPSHOT.projectionVersion,
                    InterestActivitySnapshot.class, INTEREST_SNAPSHOTS);

    private final MongoTemplate mongoTemplate;
    private final MongoQuerydslSupport querydsl;

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
        casUpsert(id, projectionVersion, update, ACTIVITY_DOCUMENT);
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
        casUpsert(id, projectionVersion, update, ACTIVITY_DOCUMENT);
    }

    public void tombstoneActivity(
            ActivityProjection activity,
            long projectionVersion,
            LocalDateTime now
    ) {
        tombstone(
                activityId(activity), projectionVersion, now,
                ACTIVITY_DOCUMENT,
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
        casUpsert(id, projectionVersion, update, COMMENT_DOCUMENT);
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
        casUpsert(id, projectionVersion, update, ARTICLE_DOCUMENT);
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
        casUpsert(id, projectionVersion, update, INTEREST_DOCUMENT);
    }

    public void hideCommentSnapshot(UUID id, long version, LocalDateTime now) {
        hideSnapshot(MongoProjectionKeyFactory.comment(id), "commentId", id, version, now,
                COMMENT_DOCUMENT);
    }

    public void hideArticleSnapshot(UUID id, long version, LocalDateTime now) {
        hideSnapshot(MongoProjectionKeyFactory.article(id), "articleId", id, version, now,
                ARTICLE_DOCUMENT);
    }

    public void hideInterestSnapshot(UUID id, long version, LocalDateTime now) {
        hideSnapshot(MongoProjectionKeyFactory.interest(id), "interestId", id, version, now,
                INTEREST_DOCUMENT);
    }

    public void tombstoneComment(UUID id, long version, LocalDateTime now) {
        tombstone(MongoProjectionKeyFactory.comment(id), version, now,
                COMMENT_DOCUMENT,
                "commentId", "articleId", "articleTitle", "authorUserId", "authorNickname",
                "content", "likeCount", "createdAt");
    }

    public void tombstoneArticle(UUID id, long version, LocalDateTime now) {
        tombstone(MongoProjectionKeyFactory.article(id), version, now,
                ARTICLE_DOCUMENT,
                "articleId", "title", "summary", "source", "sourceUrl", "publishedAt",
                "viewCount", "commentCount");
    }

    public void tombstoneInterest(UUID id, long version, LocalDateTime now) {
        tombstone(MongoProjectionKeyFactory.interest(id), version, now,
                INTEREST_DOCUMENT,
                "interestId", "name", "keywords", "subscriberCount");
    }

    public void hideActivitiesByUser(UUID userId, long version, LocalDateTime now) {
        bulkHideActivity(
                ACTIVITY.userId.eq(uuid(userId)),
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
                ACTIVITY.targetType.eq(targetType).and(ACTIVITY.targetId.eq(uuid(targetId))),
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
                ACTIVITY.parentTargetType.eq(parentType)
                        .and(ACTIVITY.parentTargetId.eq(uuid(parentId))),
                ActivityHistoryStatus.TARGET_DELETED,
                parentType,
                parentId,
                version,
                now
        );
    }

    public void hideCommentSnapshotsByAuthor(UUID userId, long version, LocalDateTime now) {
        bulkHideSnapshot(
                COMMENT_SNAPSHOT.authorUserId.eq(uuid(userId)),
                version,
                now,
                COMMENT_DOCUMENT
        );
    }

    public void hideCommentSnapshotsByArticle(UUID articleId, long version, LocalDateTime now) {
        bulkHideSnapshot(
                COMMENT_SNAPSHOT.articleId.eq(uuid(articleId)),
                version,
                now,
                COMMENT_DOCUMENT
        );
    }

    public void tombstoneActivitiesByUser(UUID userId, long version, LocalDateTime now) {
        bulkTombstoneActivity(ACTIVITY.userId.eq(uuid(userId)), version, now);
    }

    public void tombstoneActivitiesByTarget(
            ActivityTargetType targetType,
            UUID targetId,
            long version,
            LocalDateTime now
    ) {
        bulkTombstoneActivity(
                ACTIVITY.targetType.eq(targetType).and(ACTIVITY.targetId.eq(uuid(targetId))),
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
                ACTIVITY.parentTargetType.eq(parentType)
                        .and(ACTIVITY.parentTargetId.eq(uuid(parentId))),
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
                COMMENT_SNAPSHOT.authorUserId.eq(uuid(userId)), version, now);
    }

    public void tombstoneCommentSnapshotsByArticle(
            UUID articleId,
            long version,
            LocalDateTime now
    ) {
        bulkTombstoneCommentSnapshots(
                COMMENT_SNAPSHOT.articleId.eq(uuid(articleId)), version, now);
    }

    private <T> void hideSnapshot(
            String documentId,
            String naturalIdField,
            UUID naturalId,
            long version,
            LocalDateTime now,
            VersionedDocument<T> document
    ) {
        Update update = versioned(new Update(), version)
                .set(naturalIdField, uuid(naturalId))
                .set("visible", false)
                .set("tombstone", false)
                .set("updatedAt", now);
        casUpsert(documentId, version, update, document);
    }

    private void bulkHideActivity(
            BooleanExpression target,
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
                staleQuery(ACTIVITY_DOCUMENT, target.and(ACTIVITY.visible.isTrue()), version),
                update,
                ActivityHistoryDocument.class, ACTIVITY_HISTORIES);
        Update fenceOnly = versioned(new Update(), version).set("updatedAt", now);
        mongoTemplate.updateMulti(
                staleQuery(ACTIVITY_DOCUMENT, target.and(ACTIVITY.visible.isFalse()), version),
                fenceOnly,
                ActivityHistoryDocument.class, ACTIVITY_HISTORIES);
    }

    private <T> void bulkHideSnapshot(
            BooleanExpression target,
            long version,
            LocalDateTime now,
            VersionedDocument<T> document
    ) {
        Update update = versioned(new Update(), version)
                .set("visible", false)
                .set("tombstone", false)
                .set("updatedAt", now);
        mongoTemplate.updateMulti(
                staleQuery(document, target, version),
                update,
                document.type(),
                document.collection()
        );
    }

    private void bulkTombstoneActivity(
            BooleanExpression target,
            long version,
            LocalDateTime now
    ) {
        Update update = tombstoneUpdate(version, now,
                "sourceActivityId", "userId", "type", "targetType", "targetId",
                "parentTargetType", "parentTargetId", "occurredAt", "status",
                "hiddenByTargetType", "hiddenByTargetId", "createdAt");
        mongoTemplate.updateMulti(
                staleQuery(ACTIVITY_DOCUMENT, target, version), update,
                ActivityHistoryDocument.class, ACTIVITY_HISTORIES);
    }

    private void bulkTombstoneCommentSnapshots(
            BooleanExpression target,
            long version,
            LocalDateTime now
    ) {
        Update update = tombstoneUpdate(version, now,
                "commentId", "articleId", "articleTitle", "authorUserId", "authorNickname",
                "content", "likeCount", "createdAt");
        mongoTemplate.updateMulti(
                staleQuery(COMMENT_DOCUMENT, target, version), update,
                CommentActivitySnapshot.class, COMMENT_SNAPSHOTS);
    }

    private <T> void tombstone(
            String id,
            long version,
            LocalDateTime now,
            VersionedDocument<T> document,
            String... fieldsToUnset
    ) {
        Update update = tombstoneUpdate(version, now, fieldsToUnset);
        casUpsert(id, version, update, document);
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
            VersionedDocument<T> document
    ) {
        Query query = querydsl.toQuery(
                document.path(),
                document.collection(),
                document.id().eq(id).and(staleVersion(document.projectionVersion(), version))
        );
        try {
            mongoTemplate.upsert(query, update, document.type(), document.collection());
        } catch (DuplicateKeyException e) {
            Query newerOrEqual = querydsl.toQuery(
                    document.path(),
                    document.collection(),
                    document.id().eq(id).and(document.projectionVersion().goe(version))
            );
            if (!mongoTemplate.exists(
                    newerOrEqual,
                    document.type(),
                    document.collection()
            )) {
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
        Query query = staleQuery(
                ACTIVITY_DOCUMENT,
                ACTIVITY.id.eq(id).and(ACTIVITY.visible.eq(visible)),
                version
        );
        UpdateResult result = mongoTemplate.updateFirst(
                query, update, ActivityHistoryDocument.class, ACTIVITY_HISTORIES);
        return result != null && result.getMatchedCount() > 0;
    }

    private <T> Query staleQuery(
            VersionedDocument<T> document,
            BooleanExpression target,
            long version
    ) {
        return querydsl.toQuery(
                document.path(),
                document.collection(),
                target.and(staleVersion(document.projectionVersion(), version))
        );
    }

    private BooleanExpression staleVersion(NumberPath<Long> projectionVersion, long version) {
        return projectionVersion.isNull().or(projectionVersion.lt(version));
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

    private record VersionedDocument<T>(
            EntityPath<T> path,
            StringPath id,
            NumberPath<Long> projectionVersion,
            Class<T> type,
            String collection
    ) {
    }
}
