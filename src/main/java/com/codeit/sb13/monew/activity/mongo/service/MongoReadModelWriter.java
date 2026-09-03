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
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MongoReadModelWriter {

    private final MongoTemplate mongoTemplate;

    public void upsertActivity(ActivityProjection activity, LocalDateTime now) {
        Query query = activityNaturalKey(activity);
        Update update = new Update()
                .set("sourceActivityId", uuid(activity.sourceActivityId()))
                .set("visible", true)
                .set("status", ActivityHistoryStatus.ACTIVE)
                .unset("hiddenByTargetType")
                .unset("hiddenByTargetId")
                .set("updatedAt", now)
                .setOnInsert("userId", uuid(activity.userId()))
                .setOnInsert("type", activity.type())
                .setOnInsert("targetType", activity.targetType())
                .setOnInsert("targetId", uuid(activity.targetId()))
                .setOnInsert("createdAt", now)
                .max("occurredAt", activity.occurredAt());
        if (activity.parentTargetType() != null) {
            update.set("parentTargetType", activity.parentTargetType())
                    .set("parentTargetId", uuid(activity.parentTargetId()));
        }
        mongoTemplate.upsert(query, update, ActivityHistoryDocument.class, ACTIVITY_HISTORIES);
    }

    public void hideActivity(
            ActivityProjection activity,
            ActivityHistoryStatus status,
            ActivityTargetType hiddenByTargetType,
            UUID hiddenByTargetId,
            LocalDateTime now
    ) {
        Query query = activityNaturalKey(activity);
        query.addCriteria(Criteria.where("visible").is(true));
        Update update = new Update()
                .set("visible", false)
                .set("status", status)
                .set("updatedAt", now);
        if (hiddenByTargetType != null) {
            update.set("hiddenByTargetType", hiddenByTargetType)
                    .set("hiddenByTargetId", uuid(hiddenByTargetId));
        } else {
            update.unset("hiddenByTargetType").unset("hiddenByTargetId");
        }
        mongoTemplate.updateFirst(query, update, ActivityHistoryDocument.class, ACTIVITY_HISTORIES);
    }

    public void upsertCommentSnapshot(CommentState state, LocalDateTime now) {
        Query query = Query.query(Criteria.where("commentId").is(uuid(state.id())));
        Update update = new Update()
                .set("articleId", uuid(state.articleId()))
                .set("articleTitle", state.articleTitle())
                .set("authorUserId", uuid(state.authorUserId()))
                .set("authorNickname", state.authorNickname())
                .set("content", state.content())
                .set("likeCount", state.likeCount())
                .set("visible", true)
                .set("createdAt", state.createdAt())
                .set("updatedAt", state.updatedAt() == null ? now : state.updatedAt())
                .setOnInsert("commentId", uuid(state.id()));
        mongoTemplate.upsert(query, update, CommentActivitySnapshot.class, COMMENT_SNAPSHOTS);
    }

    public void upsertArticleSnapshot(ArticleState state, LocalDateTime now) {
        Query query = Query.query(Criteria.where("articleId").is(uuid(state.id())));
        Update update = new Update()
                .set("title", state.title())
                .set("summary", state.summary())
                .set("source", state.source())
                .set("sourceUrl", state.sourceUrl())
                .set("publishedAt", state.publishedAt())
                .set("viewCount", state.viewCount())
                .set("commentCount", state.commentCount())
                .set("visible", true)
                .set("updatedAt", state.updatedAt() == null ? now : state.updatedAt())
                .setOnInsert("articleId", uuid(state.id()));
        mongoTemplate.upsert(query, update, ArticleActivitySnapshot.class, ARTICLE_SNAPSHOTS);
    }

    public void upsertInterestSnapshot(InterestState state, LocalDateTime now) {
        Query query = Query.query(Criteria.where("interestId").is(uuid(state.id())));
        Update update = new Update()
                .set("name", state.name())
                .set("keywords", state.keywords())
                .set("subscriberCount", state.subscriberCount())
                .set("visible", true)
                .set("updatedAt", state.updatedAt() == null ? now : state.updatedAt())
                .setOnInsert("interestId", uuid(state.id()));
        mongoTemplate.upsert(query, update, InterestActivitySnapshot.class, INTEREST_SNAPSHOTS);
    }

    public void hideComment(UUID commentId, LocalDateTime now) {
        hideSnapshot(COMMENT_SNAPSHOTS, "commentId", commentId, CommentActivitySnapshot.class, now);
        hideActiveActivitiesByTarget(ActivityTargetType.COMMENT, commentId, now);
    }

    public void hideArticle(UUID articleId, LocalDateTime now) {
        hideSnapshot(ARTICLE_SNAPSHOTS, "articleId", articleId, ArticleActivitySnapshot.class, now);
        Query commentSnapshots = Query.query(Criteria.where("articleId").is(uuid(articleId)));
        mongoTemplate.updateMulti(
                commentSnapshots,
                new Update().set("visible", false).set("updatedAt", now),
                CommentActivitySnapshot.class,
                COMMENT_SNAPSHOTS
        );
        hideActiveActivitiesByTarget(ActivityTargetType.ARTICLE, articleId, now);
        hideActiveActivitiesByParent(ActivityTargetType.ARTICLE, articleId, now);
    }

    public void hideInterest(UUID interestId, LocalDateTime now) {
        hideSnapshot(INTEREST_SNAPSHOTS, "interestId", interestId, InterestActivitySnapshot.class, now);
        hideActiveActivitiesByTarget(ActivityTargetType.INTEREST, interestId, now);
    }

    public void hideActivitiesByUser(UUID userId, LocalDateTime now) {
        Query query = Query.query(Criteria.where("userId").is(uuid(userId)).and("visible").is(true));
        Update update = new Update()
                .set("visible", false)
                .set("status", ActivityHistoryStatus.USER_DELETED)
                .unset("hiddenByTargetType")
                .unset("hiddenByTargetId")
                .set("updatedAt", now);
        mongoTemplate.updateMulti(query, update, ActivityHistoryDocument.class, ACTIVITY_HISTORIES);
    }

    public void hideCommentSnapshotsByAuthor(UUID userId, LocalDateTime now) {
        Query query = Query.query(Criteria.where("authorUserId").is(uuid(userId)));
        mongoTemplate.updateMulti(
                query,
                new Update().set("visible", false).set("updatedAt", now),
                CommentActivitySnapshot.class,
                COMMENT_SNAPSHOTS
        );
    }

    public void updateCommentAuthorNickname(UUID userId, String nickname, LocalDateTime now) {
        Query query = Query.query(Criteria.where("authorUserId").is(uuid(userId)));
        mongoTemplate.updateMulti(
                query,
                new Update().set("authorNickname", nickname).set("updatedAt", now),
                CommentActivitySnapshot.class,
                COMMENT_SNAPSHOTS
        );
    }

    public void deleteComment(UUID commentId) {
        remove(COMMENT_SNAPSHOTS, "commentId", commentId, CommentActivitySnapshot.class);
        deleteActivitiesByTarget(ActivityTargetType.COMMENT, commentId);
    }

    public void deleteArticle(UUID articleId) {
        remove(ARTICLE_SNAPSHOTS, "articleId", articleId, ArticleActivitySnapshot.class);
        mongoTemplate.remove(
                Query.query(Criteria.where("articleId").is(uuid(articleId))),
                CommentActivitySnapshot.class,
                COMMENT_SNAPSHOTS
        );
        deleteActivitiesByTarget(ActivityTargetType.ARTICLE, articleId);
        deleteActivitiesByParent(ActivityTargetType.ARTICLE, articleId);
    }

    public void deleteInterest(UUID interestId) {
        remove(INTEREST_SNAPSHOTS, "interestId", interestId, InterestActivitySnapshot.class);
        deleteActivitiesByTarget(ActivityTargetType.INTEREST, interestId);
    }

    public void deleteUser(UUID userId) {
        mongoTemplate.remove(
                Query.query(Criteria.where("userId").is(uuid(userId))),
                ActivityHistoryDocument.class,
                ACTIVITY_HISTORIES
        );
        mongoTemplate.remove(
                Query.query(Criteria.where("authorUserId").is(uuid(userId))),
                CommentActivitySnapshot.class,
                COMMENT_SNAPSHOTS
        );
    }

    public void deleteComments(Collection<UUID> commentIds) {
        commentIds.forEach(this::deleteComment);
    }

    private void hideActiveActivitiesByTarget(
            ActivityTargetType targetType,
            UUID targetId,
            LocalDateTime now
    ) {
        Query query = Query.query(Criteria.where("targetType").is(targetType)
                .and("targetId").is(uuid(targetId))
                .and("visible").is(true));
        hideByTarget(query, targetType, targetId, now);
    }

    private void hideActiveActivitiesByParent(
            ActivityTargetType parentTargetType,
            UUID parentTargetId,
            LocalDateTime now
    ) {
        Query query = Query.query(Criteria.where("parentTargetType").is(parentTargetType)
                .and("parentTargetId").is(uuid(parentTargetId))
                .and("visible").is(true));
        hideByTarget(query, parentTargetType, parentTargetId, now);
    }

    private void hideByTarget(
            Query query,
            ActivityTargetType hiddenByTargetType,
            UUID hiddenByTargetId,
            LocalDateTime now
    ) {
        Update update = new Update()
                .set("visible", false)
                .set("status", ActivityHistoryStatus.TARGET_DELETED)
                .set("hiddenByTargetType", hiddenByTargetType)
                .set("hiddenByTargetId", uuid(hiddenByTargetId))
                .set("updatedAt", now);
        mongoTemplate.updateMulti(query, update, ActivityHistoryDocument.class, ACTIVITY_HISTORIES);
    }

    private void deleteActivitiesByTarget(ActivityTargetType targetType, UUID targetId) {
        mongoTemplate.remove(
                Query.query(Criteria.where("targetType").is(targetType)
                        .and("targetId").is(uuid(targetId))),
                ActivityHistoryDocument.class,
                ACTIVITY_HISTORIES
        );
    }

    private void deleteActivitiesByParent(ActivityTargetType parentTargetType, UUID parentTargetId) {
        mongoTemplate.remove(
                Query.query(Criteria.where("parentTargetType").is(parentTargetType)
                        .and("parentTargetId").is(uuid(parentTargetId))),
                ActivityHistoryDocument.class,
                ACTIVITY_HISTORIES
        );
    }

    private <T> void hideSnapshot(
            String collection,
            String idField,
            UUID id,
            Class<T> documentType,
            LocalDateTime now
    ) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where(idField).is(uuid(id))),
                new Update().set("visible", false).set("updatedAt", now),
                documentType,
                collection
        );
    }

    private <T> void remove(String collection, String idField, UUID id, Class<T> documentType) {
        mongoTemplate.remove(
                Query.query(Criteria.where(idField).is(uuid(id))),
                documentType,
                collection
        );
    }

    private Query activityNaturalKey(ActivityProjection activity) {
        Criteria criteria = Criteria.where("userId").is(uuid(activity.userId()))
                .and("type").is(activity.type())
                .and("targetType").is(activity.targetType())
                .and("targetId").is(uuid(activity.targetId()));
        return Query.query(criteria);
    }

    private String uuid(UUID id) {
        return id == null ? null : id.toString();
    }
}
