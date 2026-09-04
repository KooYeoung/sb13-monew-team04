package com.codeit.sb13.monew.activity.mongo.backfill;

import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.ACTIVITY_HISTORIES;
import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.ARTICLE_SNAPSHOTS;
import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.COMMENT_SNAPSHOTS;
import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.INTEREST_SNAPSHOTS;

import com.codeit.sb13.monew.activity.mongo.backfill.ReadModelBackfillVerificationReport.StageVerification;
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
import com.codeit.sb13.monew.activity.outbox.worker.source.OutboxProjectionSourceReader;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.ArticleState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.CommentState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.InterestState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.RelationKey;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.RelationState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.UserState;
import com.codeit.sb13.monew.global.exception.readmodel.ReadModelBackfillProgressException;
import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.dsl.StringPath;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 활성 RDB 활동과 MongoDB 활동·snapshot의 현재 상태 및 집계값을 대조한다. */
@Component
@RequiredArgsConstructor
public class ReadModelBackfillVerifier {

    private static final long VERIFICATION_VERSION = 0L;
    private static final QActivityHistoryDocument ACTIVITY =
            QActivityHistoryDocument.activityHistoryDocument;
    private static final QInterestActivitySnapshot INTEREST_SNAPSHOT =
            QInterestActivitySnapshot.interestActivitySnapshot;
    private static final QCommentActivitySnapshot COMMENT_SNAPSHOT =
            QCommentActivitySnapshot.commentActivitySnapshot;
    private static final QArticleActivitySnapshot ARTICLE_SNAPSHOT =
            QArticleActivitySnapshot.articleActivitySnapshot;

    private final ReadModelBackfillScanner scanner;
    private final OutboxProjectionSourceReader sourceReader;
    private final MongoQuerydslSupport querydsl;

    /**
     * 모든 초기 투영 stage를 다시 keyset scan하여 MongoDB Read Model과 비교한다.
     *
     * <p>성능 측정값은 수집하지 않는다. 활동 수, 노출 상태, 결정적 ID 및 해당 활동이
     * 참조하는 snapshot의 현재 집계값만 검증한다.</p>
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReadModelBackfillVerificationReport verify(
            int batchSize,
            Runnable claimHealthCheck
    ) {
        Map<ReadModelBackfillStage, StageVerification> results =
                new EnumMap<>(ReadModelBackfillStage.class);
        for (ReadModelBackfillStage stage : ReadModelBackfillStage.values()) {
            results.put(stage, verifyStage(stage, batchSize, claimHealthCheck));
        }
        return new ReadModelBackfillVerificationReport(results);
    }

    private StageVerification verifyStage(
            ReadModelBackfillStage stage,
            int batchSize,
            Runnable claimHealthCheck
    ) {
        UUID cursor = null;
        Set<UUID> visitedCursors = new HashSet<>();
        long expected = 0;
        long invalidActivities = 0;
        long snapshotChecks = 0;
        long snapshotMismatches = 0;

        while (true) {
            claimHealthCheck.run();
            InitialProjectionPage page = scanner.scan(
                    stage, cursor, null, batchSize, VERIFICATION_VERSION);
            if (page.isEmpty()) {
                break;
            }
            UUID nextCursor = nextCursor(stage, cursor, page, visitedCursors);

            ProjectionSourceBatch source = sourceReader.read(page.events());
            claimHealthCheck.run();
            List<InitialProjectionEvent> visibleEvents = page.events().stream()
                    .filter(event -> shouldBeVisible(stage, event, source))
                    .toList();
            Map<String, ActivityHistoryDocument> activities = readActivities(
                    visibleEvents.stream().map(event -> activityId(stage, event)).toList());

            expected += visibleEvents.size();
            invalidActivities += visibleEvents.stream()
                    .filter(event -> !validActivity(
                            stage, event, activities.get(activityId(stage, event))))
                    .count();
            SnapshotVerification snapshots = verifySnapshots(stage, visibleEvents, source);
            snapshotChecks += snapshots.checks();
            snapshotMismatches += snapshots.mismatches();
            cursor = nextCursor;
        }

        claimHealthCheck.run();
        return new StageVerification(
                expected,
                countVisibleActivities(stage),
                invalidActivities,
                snapshotChecks,
                snapshotMismatches
        );
    }

    private UUID nextCursor(
            ReadModelBackfillStage stage,
            UUID currentCursor,
            InitialProjectionPage page,
            Set<UUID> visitedCursors
    ) {
        UUID nextCursor = page.lastSourceRowId();
        UUID lastEventId = page.events().get(page.events().size() - 1).sourceRowId();
        if (nextCursor == null) {
            throw progressFailure(stage, currentCursor, null, "LAST_CURSOR_MISSING");
        }
        if (!nextCursor.equals(lastEventId)) {
            throw progressFailure(
                    stage, currentCursor, nextCursor, "LAST_EVENT_CURSOR_MISMATCH");
        }
        if (nextCursor.equals(currentCursor)) {
            throw progressFailure(stage, currentCursor, nextCursor, "CURSOR_NOT_CHANGED");
        }
        if (!visitedCursors.add(nextCursor)) {
            throw progressFailure(stage, currentCursor, nextCursor, "CURSOR_REPEATED");
        }
        return nextCursor;
    }

    private ReadModelBackfillProgressException progressFailure(
            ReadModelBackfillStage stage,
            UUID currentCursor,
            UUID nextCursor,
            String reason
    ) {
        return new ReadModelBackfillProgressException(
                stage, currentCursor, nextCursor, reason);
    }

    private boolean shouldBeVisible(
            ReadModelBackfillStage stage,
            InitialProjectionEvent event,
            ProjectionSourceBatch source
    ) {
        UserState actor = source.users().get(event.actorUserId());
        if (actor == null || !actor.active()) {
            return false;
        }
        return switch (stage) {
            case SUBSCRIPTION -> source.interests().containsKey(event.aggregateId())
                    && active(source.subscriptions().get(relationKey(event)));
            case COMMENT_WRITTEN -> visible(source.comments().get(event.aggregateId()));
            case COMMENT_LIKED -> visible(source.comments().get(event.aggregateId()))
                    && active(source.commentLikes().get(relationKey(event)));
            case ARTICLE_VIEWED -> visible(source.articles().get(event.aggregateId()))
                    && active(source.articleViews().get(relationKey(event)));
        };
    }

    private boolean validActivity(
            ReadModelBackfillStage stage,
            InitialProjectionEvent event,
            ActivityHistoryDocument document
    ) {
        return document != null
                && document.visible()
                && !document.tombstone()
                && document.status() == ActivityHistoryStatus.ACTIVE
                && document.type() == activityType(stage)
                && document.targetType() == targetType(stage)
                && Objects.equals(document.userId(), event.actorUserId().toString())
                && Objects.equals(document.targetId(), event.aggregateId().toString())
                && Objects.equals(document.sourceActivityId(), event.sourceRowId().toString());
    }

    private SnapshotVerification verifySnapshots(
            ReadModelBackfillStage stage,
            List<InitialProjectionEvent> events,
            ProjectionSourceBatch source
    ) {
        if (events.isEmpty()) {
            return SnapshotVerification.EMPTY;
        }
        return switch (stage) {
            case SUBSCRIPTION -> verifyInterestSnapshots(events, source);
            case COMMENT_WRITTEN, COMMENT_LIKED -> verifyCommentSnapshots(events, source);
            case ARTICLE_VIEWED -> verifyArticleSnapshots(events, source);
        };
    }

    private SnapshotVerification verifyInterestSnapshots(
            List<InitialProjectionEvent> events,
            ProjectionSourceBatch source
    ) {
        Map<String, InterestActivitySnapshot> documents = readByIds(
                events.stream()
                        .map(event -> MongoProjectionKeyFactory.interest(event.aggregateId()))
                        .distinct()
                        .toList(),
                INTEREST_SNAPSHOT,
                INTEREST_SNAPSHOT.id,
                INTEREST_SNAPSHOTS,
                InterestActivitySnapshot::id
        );
        long mismatches = events.stream().filter(event -> {
            InterestState state = source.interests().get(event.aggregateId());
            InterestActivitySnapshot document = documents.get(
                    MongoProjectionKeyFactory.interest(event.aggregateId()));
            return document == null
                    || !document.visible()
                    || document.tombstone()
                    || !Objects.equals(document.interestId(), event.aggregateId().toString())
                    || document.subscriberCount() != state.subscriberCount();
        }).count();
        return new SnapshotVerification(events.size(), mismatches);
    }

    private SnapshotVerification verifyCommentSnapshots(
            List<InitialProjectionEvent> events,
            ProjectionSourceBatch source
    ) {
        Map<String, CommentActivitySnapshot> documents = readByIds(
                events.stream()
                        .map(event -> MongoProjectionKeyFactory.comment(event.aggregateId()))
                        .distinct()
                        .toList(),
                COMMENT_SNAPSHOT,
                COMMENT_SNAPSHOT.id,
                COMMENT_SNAPSHOTS,
                CommentActivitySnapshot::id
        );
        long mismatches = events.stream().filter(event -> {
            CommentState state = source.comments().get(event.aggregateId());
            CommentActivitySnapshot document = documents.get(
                    MongoProjectionKeyFactory.comment(event.aggregateId()));
            return document == null
                    || !document.visible()
                    || document.tombstone()
                    || !Objects.equals(document.commentId(), event.aggregateId().toString())
                    || document.likeCount() != state.likeCount();
        }).count();
        return new SnapshotVerification(events.size(), mismatches);
    }

    private SnapshotVerification verifyArticleSnapshots(
            List<InitialProjectionEvent> events,
            ProjectionSourceBatch source
    ) {
        Map<String, ArticleActivitySnapshot> documents = readByIds(
                events.stream()
                        .map(event -> MongoProjectionKeyFactory.article(event.aggregateId()))
                        .distinct()
                        .toList(),
                ARTICLE_SNAPSHOT,
                ARTICLE_SNAPSHOT.id,
                ARTICLE_SNAPSHOTS,
                ArticleActivitySnapshot::id
        );
        long mismatches = events.stream().filter(event -> {
            ArticleState state = source.articles().get(event.aggregateId());
            ArticleActivitySnapshot document = documents.get(
                    MongoProjectionKeyFactory.article(event.aggregateId()));
            return document == null
                    || !document.visible()
                    || document.tombstone()
                    || !Objects.equals(document.articleId(), event.aggregateId().toString())
                    || document.viewCount() != state.viewCount()
                    || document.commentCount() != state.commentCount();
        }).count();
        return new SnapshotVerification(events.size(), mismatches);
    }

    private Map<String, ActivityHistoryDocument> readActivities(List<String> ids) {
        return readByIds(
                ids,
                ACTIVITY,
                ACTIVITY.id,
                ACTIVITY_HISTORIES,
                ActivityHistoryDocument::id
        );
    }

    private <T> Map<String, T> readByIds(
            List<String> ids,
            EntityPath<T> documentPath,
            StringPath idPath,
            String collection,
            Function<T, String> idExtractor
    ) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return querydsl.fetch(documentPath, collection, idPath.in(ids))
                .stream()
                .collect(Collectors.toMap(idExtractor, Function.identity()));
    }

    private long countVisibleActivities(ReadModelBackfillStage stage) {
        return querydsl.count(
                ACTIVITY,
                ACTIVITY_HISTORIES,
                ACTIVITY.type.eq(activityType(stage))
                        .and(ACTIVITY.targetType.eq(targetType(stage)))
                        .and(ACTIVITY.visible.isTrue())
                        .and(ACTIVITY.tombstone.isFalse())
        );
    }

    private String activityId(
            ReadModelBackfillStage stage,
            InitialProjectionEvent event
    ) {
        return MongoProjectionKeyFactory.activity(
                event.actorUserId(),
                activityType(stage),
                targetType(stage),
                event.aggregateId()
        );
    }

    private ActivityHistoryType activityType(ReadModelBackfillStage stage) {
        return switch (stage) {
            case SUBSCRIPTION -> ActivityHistoryType.INTEREST_SUBSCRIBED;
            case COMMENT_WRITTEN -> ActivityHistoryType.COMMENT_WRITTEN;
            case COMMENT_LIKED -> ActivityHistoryType.COMMENT_LIKED;
            case ARTICLE_VIEWED -> ActivityHistoryType.ARTICLE_VIEWED;
        };
    }

    private ActivityTargetType targetType(ReadModelBackfillStage stage) {
        return switch (stage) {
            case SUBSCRIPTION -> ActivityTargetType.INTEREST;
            case COMMENT_WRITTEN, COMMENT_LIKED -> ActivityTargetType.COMMENT;
            case ARTICLE_VIEWED -> ActivityTargetType.ARTICLE;
        };
    }

    private RelationKey relationKey(InitialProjectionEvent event) {
        return new RelationKey(event.aggregateId(), event.actorUserId());
    }

    private boolean active(RelationState relation) {
        return relation != null && relation.active();
    }

    private boolean visible(CommentState state) {
        return state != null && state.visible();
    }

    private boolean visible(ArticleState state) {
        return state != null && state.visible();
    }

    private record SnapshotVerification(long checks, long mismatches) {
        private static final SnapshotVerification EMPTY = new SnapshotVerification(0, 0);
    }
}
