package com.codeit.sb13.monew.activity.mongo.query;

import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.ACTIVITY_HISTORIES;
import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.ARTICLE_SNAPSHOTS;
import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.COMMENT_SNAPSHOTS;
import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.INTEREST_SNAPSHOTS;

import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryDocument;
import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryStatus;
import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryType;
import com.codeit.sb13.monew.activity.mongo.document.ActivityTargetType;
import com.codeit.sb13.monew.activity.mongo.document.ArticleActivitySnapshot;
import com.codeit.sb13.monew.activity.mongo.document.CommentActivitySnapshot;
import com.codeit.sb13.monew.activity.mongo.document.InterestActivitySnapshot;
import com.codeit.sb13.monew.activity.mongo.document.QActivityHistoryDocument;
import com.codeit.sb13.monew.activity.mongo.document.QArticleActivitySnapshot;
import com.codeit.sb13.monew.activity.mongo.document.QCommentActivitySnapshot;
import com.codeit.sb13.monew.activity.mongo.document.QInterestActivitySnapshot;
import com.codeit.sb13.monew.activity.mongo.querydsl.MongoQuerydslSupport;
import com.codeit.sb13.monew.activity.mongo.service.MongoProjectionKeyFactory;
import com.codeit.sb13.monew.activity.service.dto.RecentArticle;
import com.codeit.sb13.monew.activity.service.dto.RecentComment;
import com.codeit.sb13.monew.activity.service.dto.RecentCommentLike;
import com.codeit.sb13.monew.activity.service.dto.RecentSubscribed;
import com.codeit.sb13.monew.global.exception.readmodel.ReadModelDocumentMappingException;
import com.codeit.sb13.monew.global.exception.readmodel.ReadModelQueryConditionInvalidException;
import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.StringPath;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 사용자별 MongoDB 활동을 복합 cursor로 조회하고 대상 snapshot을 기존 API DTO로 매핑한다.
 *
 * <p>activity page를 먼저 확정한 뒤 snapshot을 조회한다. snapshot이 없거나 노출할 수
 * 없더라도 빠진 수만큼 activity를 추가 조회하지 않으며, cursor는 응답 DTO가 아닌 마지막으로
 * 스캔한 activity를 기준으로 이동한다.</p>
 */
@Component
@RequiredArgsConstructor
public class MongoActivityQueryReader {

    private static final QActivityHistoryDocument ACTIVITY =
            QActivityHistoryDocument.activityHistoryDocument;
    private static final QInterestActivitySnapshot INTEREST_SNAPSHOT =
            QInterestActivitySnapshot.interestActivitySnapshot;
    private static final QCommentActivitySnapshot COMMENT_SNAPSHOT =
            QCommentActivitySnapshot.commentActivitySnapshot;
    private static final QArticleActivitySnapshot ARTICLE_SNAPSHOT =
            QArticleActivitySnapshot.articleActivitySnapshot;

    private final MongoQuerydslSupport querydsl;

    public ActivityReadPage<RecentSubscribed> readSubscriptions(ActivityReadRequest request) {
        ActivityCandidates candidates = readActivities(
                request,
                ActivityHistoryType.INTEREST_SUBSCRIBED,
                ActivityTargetType.INTEREST
        );
        Map<String, InterestActivitySnapshot> snapshots = readSnapshots(
                candidates.activities(),
                MongoProjectionKeyFactory::interest,
                INTEREST_SNAPSHOT,
                INTEREST_SNAPSHOT.id,
                INTEREST_SNAPSHOTS,
                InterestActivitySnapshot::id
        );
        List<RecentSubscribed> content = candidates.activities().stream()
                .map(activity -> mapSubscription(activity, snapshots))
                .filter(Objects::nonNull)
                .toList();
        return candidates.toPage(content);
    }

    public ActivityReadPage<RecentComment> readComments(ActivityReadRequest request) {
        ActivityCandidates candidates = readActivities(
                request,
                ActivityHistoryType.COMMENT_WRITTEN,
                ActivityTargetType.COMMENT
        );
        Map<String, CommentActivitySnapshot> snapshots = readSnapshots(
                candidates.activities(),
                MongoProjectionKeyFactory::comment,
                COMMENT_SNAPSHOT,
                COMMENT_SNAPSHOT.id,
                COMMENT_SNAPSHOTS,
                CommentActivitySnapshot::id
        );
        List<RecentComment> content = candidates.activities().stream()
                .map(activity -> mapComment(activity, snapshots))
                .filter(Objects::nonNull)
                .toList();
        return candidates.toPage(content);
    }

    public ActivityReadPage<RecentCommentLike> readCommentLikes(ActivityReadRequest request) {
        ActivityCandidates candidates = readActivities(
                request,
                ActivityHistoryType.COMMENT_LIKED,
                ActivityTargetType.COMMENT
        );
        Map<String, CommentActivitySnapshot> snapshots = readSnapshots(
                candidates.activities(),
                MongoProjectionKeyFactory::comment,
                COMMENT_SNAPSHOT,
                COMMENT_SNAPSHOT.id,
                COMMENT_SNAPSHOTS,
                CommentActivitySnapshot::id
        );
        List<RecentCommentLike> content = candidates.activities().stream()
                .map(activity -> mapCommentLike(activity, snapshots))
                .filter(Objects::nonNull)
                .toList();
        return candidates.toPage(content);
    }

    public ActivityReadPage<RecentArticle> readArticleViews(ActivityReadRequest request) {
        ActivityCandidates candidates = readActivities(
                request,
                ActivityHistoryType.ARTICLE_VIEWED,
                ActivityTargetType.ARTICLE
        );
        Map<String, ArticleActivitySnapshot> snapshots = readSnapshots(
                candidates.activities(),
                MongoProjectionKeyFactory::article,
                ARTICLE_SNAPSHOT,
                ARTICLE_SNAPSHOT.id,
                ARTICLE_SNAPSHOTS,
                ArticleActivitySnapshot::id
        );
        List<RecentArticle> content = candidates.activities().stream()
                .map(activity -> mapArticle(activity, snapshots))
                .filter(Objects::nonNull)
                .toList();
        return candidates.toPage(content);
    }

    private ActivityCandidates readActivities(
            ActivityReadRequest request,
            ActivityHistoryType type,
            ActivityTargetType targetType
    ) {
        BooleanExpression predicate = ACTIVITY.userId.eq(request.userId().toString())
                .and(ACTIVITY.type.eq(type))
                .and(ACTIVITY.targetType.eq(targetType))
                .and(ACTIVITY.visible.isTrue())
                .and(ACTIVITY.status.eq(ActivityHistoryStatus.ACTIVE))
                .and(ACTIVITY.tombstone.isFalse());
        if (request.cursor() != null) {
            BooleanExpression cursorPredicate = ACTIVITY.occurredAt
                    .lt(request.cursor().occurredAt())
                    .or(ACTIVITY.occurredAt.eq(request.cursor().occurredAt())
                            .and(ACTIVITY.id.lt(request.cursor().activityId())));
            predicate = predicate.and(cursorPredicate);
        }

        List<ActivityHistoryDocument> fetched = querydsl.fetch(
                ACTIVITY,
                ACTIVITY_HISTORIES,
                predicate,
                request.limit() + 1L,
                ACTIVITY.occurredAt.desc(),
                ACTIVITY.id.desc()
        );
        boolean hasNext = fetched.size() > request.limit();
        List<ActivityHistoryDocument> activities = hasNext
                ? List.copyOf(fetched.subList(0, request.limit()))
                : List.copyOf(fetched);
        ActivityReadCursor nextCursor = hasNext
                ? cursorOf(activities.get(activities.size() - 1))
                : null;
        return new ActivityCandidates(activities, nextCursor, hasNext);
    }

    private <T> Map<String, T> readSnapshots(
            List<ActivityHistoryDocument> activities,
            Function<UUID, String> keyFactory,
            EntityPath<T> documentPath,
            StringPath idPath,
            String collection,
            Function<T, String> idExtractor
    ) {
        List<String> snapshotIds = activities.stream()
                .map(activity -> keyFactory.apply(uuid(
                        activity.id(), "targetId", activity.targetId()
                )))
                .distinct()
                .toList();
        if (snapshotIds.isEmpty()) {
            return Map.of();
        }
        return querydsl.fetch(documentPath, collection, idPath.in(snapshotIds))
                .stream()
                .collect(Collectors.toMap(idExtractor, Function.identity()));
    }

    private RecentSubscribed mapSubscription(
            ActivityHistoryDocument activity,
            Map<String, InterestActivitySnapshot> snapshots
    ) {
        UUID targetId = uuid(activity.id(), "targetId", activity.targetId());
        InterestActivitySnapshot snapshot = snapshots.get(
                MongoProjectionKeyFactory.interest(targetId)
        );
        if (!visible(snapshot) || !Objects.equals(snapshot.interestId(), activity.targetId())) {
            return null;
        }
        return new RecentSubscribed(
                uuid(activity.id(), "sourceActivityId", activity.sourceActivityId()),
                activity.occurredAt(),
                targetId,
                snapshot.name(),
                copyKeywords(snapshot, activity.id()),
                snapshot.subscriberCount()
        );
    }

    private RecentComment mapComment(
            ActivityHistoryDocument activity,
            Map<String, CommentActivitySnapshot> snapshots
    ) {
        UUID targetId = uuid(activity.id(), "targetId", activity.targetId());
        CommentActivitySnapshot snapshot = snapshots.get(
                MongoProjectionKeyFactory.comment(targetId)
        );
        if (!visible(snapshot) || !Objects.equals(snapshot.commentId(), activity.targetId())) {
            return null;
        }
        return new RecentComment(
                uuid(activity.id(), "sourceActivityId", activity.sourceActivityId()),
                uuid(snapshot.id(), "articleId", snapshot.articleId()),
                snapshot.articleTitle(),
                uuid(snapshot.id(), "authorUserId", snapshot.authorUserId()),
                snapshot.authorNickname(),
                snapshot.content(),
                snapshot.likeCount(),
                snapshot.createdAt()
        );
    }

    private RecentCommentLike mapCommentLike(
            ActivityHistoryDocument activity,
            Map<String, CommentActivitySnapshot> snapshots
    ) {
        UUID targetId = uuid(activity.id(), "targetId", activity.targetId());
        CommentActivitySnapshot snapshot = snapshots.get(
                MongoProjectionKeyFactory.comment(targetId)
        );
        if (!visible(snapshot) || !Objects.equals(snapshot.commentId(), activity.targetId())) {
            return null;
        }
        return new RecentCommentLike(
                uuid(activity.id(), "sourceActivityId", activity.sourceActivityId()),
                activity.occurredAt(),
                targetId,
                uuid(snapshot.id(), "articleId", snapshot.articleId()),
                snapshot.articleTitle(),
                uuid(snapshot.id(), "authorUserId", snapshot.authorUserId()),
                snapshot.authorNickname(),
                snapshot.content(),
                snapshot.likeCount(),
                snapshot.createdAt()
        );
    }

    private RecentArticle mapArticle(
            ActivityHistoryDocument activity,
            Map<String, ArticleActivitySnapshot> snapshots
    ) {
        UUID targetId = uuid(activity.id(), "targetId", activity.targetId());
        ArticleActivitySnapshot snapshot = snapshots.get(
                MongoProjectionKeyFactory.article(targetId)
        );
        if (!visible(snapshot) || !Objects.equals(snapshot.articleId(), activity.targetId())) {
            return null;
        }
        return new RecentArticle(
                uuid(activity.id(), "sourceActivityId", activity.sourceActivityId()),
                uuid(activity.id(), "userId", activity.userId()),
                activity.occurredAt(),
                targetId,
                snapshot.source(),
                snapshot.sourceUrl(),
                snapshot.title(),
                snapshot.publishedAt(),
                snapshot.summary(),
                snapshot.commentCount(),
                snapshot.viewCount()
        );
    }

    private ActivityReadCursor cursorOf(ActivityHistoryDocument activity) {
        try {
            return new ActivityReadCursor(activity.occurredAt(), activity.id());
        } catch (ReadModelQueryConditionInvalidException e) {
            throw new ReadModelDocumentMappingException(
                    activity.id(), "cursor", activity.occurredAt() + ":" + activity.id(), e
            );
        }
    }

    private UUID uuid(String documentId, String field, String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException e) {
            throw new ReadModelDocumentMappingException(documentId, field, value, e);
        }
    }

    private List<String> copyKeywords(InterestActivitySnapshot snapshot, String activityId) {
        try {
            return List.copyOf(snapshot.keywords());
        } catch (RuntimeException e) {
            throw new ReadModelDocumentMappingException(
                    activityId, "snapshot.keywords", snapshot.keywords(), e
            );
        }
    }

    private boolean visible(InterestActivitySnapshot snapshot) {
        return snapshot != null && snapshot.visible() && !snapshot.tombstone();
    }

    private boolean visible(CommentActivitySnapshot snapshot) {
        return snapshot != null && snapshot.visible() && !snapshot.tombstone();
    }

    private boolean visible(ArticleActivitySnapshot snapshot) {
        return snapshot != null && snapshot.visible() && !snapshot.tombstone();
    }

    private record ActivityCandidates(
            List<ActivityHistoryDocument> activities,
            ActivityReadCursor nextCursor,
            boolean hasNext
    ) {
        private <T> ActivityReadPage<T> toPage(List<T> content) {
            return new ActivityReadPage<>(content, nextCursor, hasNext);
        }
    }
}
