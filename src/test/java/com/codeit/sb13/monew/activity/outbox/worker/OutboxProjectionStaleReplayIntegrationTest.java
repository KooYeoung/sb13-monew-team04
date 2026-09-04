package com.codeit.sb13.monew.activity.outbox.worker;

import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.ACTIVITY_HISTORIES;
import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.ARTICLE_SNAPSHOTS;
import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.COMMENT_SNAPSHOTS;
import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.INTEREST_SNAPSHOTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryType;
import com.codeit.sb13.monew.activity.mongo.document.ActivityTargetType;
import com.codeit.sb13.monew.activity.mongo.service.ActivityProjection;
import com.codeit.sb13.monew.activity.mongo.service.MongoProjectionKeyFactory;
import com.codeit.sb13.monew.activity.mongo.service.MongoReadModelWriter;
import com.codeit.sb13.monew.activity.mongo.querydsl.MongoQuerydslSupport;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxAggregateType;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEvent;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventStatus;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventType;
import com.codeit.sb13.monew.activity.outbox.payload.ActivityProjectionKeyPayload;
import com.codeit.sb13.monew.activity.outbox.payload.ArticleOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.payload.CommentOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.payload.InterestOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.payload.OutboxEventPayload;
import com.codeit.sb13.monew.activity.outbox.payload.ProjectionImpact;
import com.codeit.sb13.monew.activity.outbox.payload.UserHardDeleteOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.service.OutboxPayloadSerializer;
import com.codeit.sb13.monew.activity.outbox.worker.claim.OutboxClaimBatch;
import com.codeit.sb13.monew.activity.outbox.worker.claim.OutboxClaimHeartbeat;
import com.codeit.sb13.monew.activity.outbox.worker.claim.OutboxClaimLease;
import com.codeit.sb13.monew.activity.outbox.worker.claim.OutboxClaimService;
import com.codeit.sb13.monew.activity.outbox.worker.config.OutboxWorkerProperties;
import com.codeit.sb13.monew.activity.outbox.worker.source.OutboxProjectionSourceReader;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.ArticleState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.CommentState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.InterestState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.RelationKey;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.RelationState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.UserState;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.Filters;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.opentest4j.TestAbortedException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

@DisplayName("Outbox 물리삭제와 stale replay 통합 테스트")
class OutboxProjectionStaleReplayIntegrationTest {

    private static final DockerImageName MONGO_IMAGE = DockerImageName.parse("mongo:8.0");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-04T01:00:00Z"),
            ZoneOffset.UTC
    );
    private static GenericContainer<?> mongo;

    private final UUID claimId = UUID.randomUUID();
    private MongoClient mongoClient;
    private MongoTemplate mongoTemplate;
    private MongoReadModelWriter writer;
    private OutboxProjectionHandler handler;
    private OutboxClaimService claimService;
    private OutboxClaimHeartbeat claimHeartbeat;
    private OutboxClaimLease claimLease;
    private OutboxProjectionSourceReader sourceReader;
    private OutboxEventStateService stateService;
    private OutboxWorker worker;
    private OutboxPayloadSerializer payloadSerializer;

    @BeforeAll
    static void startMongo() {
        if (!dockerAvailable()) {
            throw new TestAbortedException("Docker daemon is not available; skipping MongoDB test");
        }
        mongo = new GenericContainer<>(MONGO_IMAGE).withExposedPorts(27017);
        mongo.start();
    }

    @AfterAll
    static void stopMongo() {
        if (mongo != null) {
            mongo.stop();
        }
    }

    @BeforeEach
    void setUp() {
        String uri = "mongodb://" + mongo.getHost() + ':' + mongo.getMappedPort(27017);
        mongoClient = MongoClients.create(uri);
        mongoTemplate = new MongoTemplate(mongoClient, "monew-stale-" + UUID.randomUUID());
        writer = new MongoReadModelWriter(
                mongoTemplate,
                new MongoQuerydslSupport(mongoTemplate)
        );
        handler = new OutboxProjectionHandler(writer);
        payloadSerializer = new OutboxPayloadSerializer(new ObjectMapper());

        claimService = mock(OutboxClaimService.class);
        claimHeartbeat = mock(OutboxClaimHeartbeat.class);
        claimLease = mock(OutboxClaimLease.class);
        sourceReader = mock(OutboxProjectionSourceReader.class);
        stateService = mock(OutboxEventStateService.class);
        worker = new OutboxWorker(
                claimService,
                claimHeartbeat,
                new OutboxEventDecoder(payloadSerializer),
                sourceReader,
                handler,
                stateService,
                new OutboxWorkerProperties(
                        true,
                        1000,
                        10,
                        Duration.ofMinutes(5),
                        Duration.ofMinutes(1)
                ),
                CLOCK
        );
        given(claimHeartbeat.start(claimId)).willReturn(claimLease);
    }

    @AfterEach
    void tearDown() {
        if (mongoTemplate != null) {
            mongoTemplate.getDb().drop();
        }
        if (mongoClient != null) {
            mongoClient.close();
        }
    }

    @ParameterizedTest(name = "{0} stale 이벤트")
    @EnumSource(value = OutboxEventStatus.class, names = {"PENDING", "FAILED"})
    @DisplayName("물리삭제보다 낮은 버전의 PENDING과 FAILED는 tombstone을 재생성하지 않는다")
    void pendingAndFailedReplayCannotRecreateTombstone(OutboxEventStatus status) {
        UUID articleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ActivityProjection activity = articleView(userId, articleId);
        writer.upsertArticleSnapshot(article(articleId, "삭제 전 기사"), 1L, now());
        writer.upsertActivity(activity, 1L, now());
        writer.tombstoneArticle(articleId, 3L, now().plusMinutes(1));
        writer.tombstoneActivity(activity, 3L, now().plusMinutes(1));

        OutboxEvent stale = outboxEvent(
                OutboxEventType.ARTICLE_VIEWED,
                OutboxAggregateType.ARTICLE,
                articleId,
                userId,
                new ArticleOutboxPayload(OutboxEventAction.VIEWED),
                2L
        );
        if (status == OutboxEventStatus.FAILED) {
            stale.markFailed("temporary failure", now().minusMinutes(1));
        }
        given(claimService.claim(10, Duration.ofMinutes(5)))
                .willReturn(new OutboxClaimBatch(claimId, List.of(stale)));
        given(sourceReader.read(anyList())).willReturn(emptySource());

        OutboxWorkerResult result = worker.runOnce();

        assertThat(stale.getStatus()).isEqualTo(status);
        assertThat(result).isEqualTo(new OutboxWorkerResult(1, 1, 0));
        assertScrubbed(articleDocument(articleId), 3L, "articleId", "title", "summary");
        assertScrubbed(activityDocument(activity), 3L, "userId", "targetId", "type");
        verify(stateService).markProcessed(stale.getId(), claimId, now());
    }

    @Test
    @DisplayName("삭제 전에 읽은 원본 상태를 늦게 쓰더라도 높은 버전 tombstone이 유지된다")
    void inFlightStaleSourceCannotOverwriteNewerTombstone() {
        UUID articleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID viewId = UUID.randomUUID();
        ActivityProjection activity = articleView(userId, articleId);
        writer.upsertArticleSnapshot(article(articleId, "삭제 전 기사"), 1L, now());
        writer.upsertActivity(activity, 1L, now());
        writer.tombstoneArticle(articleId, 5L, now().plusMinutes(1));
        writer.tombstoneActivity(activity, 5L, now().plusMinutes(1));

        OutboxEvent stale = outboxEvent(
                OutboxEventType.ARTICLE_VIEWED,
                OutboxAggregateType.ARTICLE,
                articleId,
                userId,
                new ArticleOutboxPayload(OutboxEventAction.VIEWED),
                4L
        );
        ProjectionSourceBatch staleSource = new ProjectionSourceBatch(
                Map.of(userId, new UserState(userId, "삭제 전 사용자", true)),
                Map.of(),
                Map.of(),
                Map.of(articleId, article(articleId, "오래된 조회 결과")),
                Map.of(),
                Map.of(),
                Map.of(
                        new RelationKey(articleId, userId),
                        new RelationState(viewId, articleId, userId, true, now())
                )
        );
        given(claimService.claim(10, Duration.ofMinutes(5)))
                .willReturn(new OutboxClaimBatch(claimId, List.of(stale)));
        given(sourceReader.read(anyList())).willReturn(staleSource);

        OutboxWorkerResult result = worker.runOnce();

        assertThat(result).isEqualTo(new OutboxWorkerResult(1, 1, 0));
        assertScrubbed(articleDocument(articleId), 5L, "articleId", "title", "summary");
        assertScrubbed(activityDocument(activity), 5L, "userId", "targetId", "type");
        verify(stateService).markProcessed(stale.getId(), claimId, now());
    }

    @Test
    @DisplayName("도메인별 물리삭제는 기존 문서와 payload fan-out key를 scrubbed tombstone으로 정리한다")
    void hardDeleteHandlersScrubAllAggregateFanOuts() {
        assertCommentHardDeleteCleanup();
        assertArticleHardDeleteCleanup();
        assertInterestHardDeleteCleanup();
        assertUserHardDeleteCleanup();
    }

    private void assertCommentHardDeleteCleanup() {
        UUID articleId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID likerId = UUID.randomUUID();
        ActivityProjection written = commentActivity(
                authorId, commentId, articleId, ActivityHistoryType.COMMENT_WRITTEN);
        ActivityProjection capturedLike = commentActivity(
                likerId, commentId, articleId, ActivityHistoryType.COMMENT_LIKED);
        writer.upsertCommentSnapshot(comment(commentId, articleId, authorId), 1L, now());
        writer.upsertActivity(written, 1L, now());

        ProjectionImpact impact = new ProjectionImpact(
                List.of(new ActivityProjectionKeyPayload(
                        likerId, OutboxEventType.COMMENT_LIKED, commentId)),
                List.of(commentId)
        );
        handler.project(deletionEvent(
                OutboxEventType.COMMENT_HARD_DELETED,
                OutboxAggregateType.COMMENT,
                commentId,
                new CommentOutboxPayload(articleId, OutboxEventAction.HARD_DELETED, impact)
        ), emptySource(), now());

        assertScrubbed(commentDocument(commentId), 10L, "commentId", "content", "authorUserId");
        assertScrubbed(activityDocument(written), 10L, "userId", "targetId", "type");
        assertScrubbed(activityDocument(capturedLike), 10L, "userId", "targetId", "type");
    }

    private void assertArticleHardDeleteCleanup() {
        UUID articleId = UUID.randomUUID();
        UUID childCommentId = UUID.randomUUID();
        UUID capturedCommentId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ActivityProjection direct = articleView(UUID.randomUUID(), articleId);
        ActivityProjection child = commentActivity(
                UUID.randomUUID(), childCommentId, articleId, ActivityHistoryType.COMMENT_LIKED);
        writer.upsertArticleSnapshot(article(articleId, "삭제 대상 기사"), 1L, now());
        writer.upsertCommentSnapshot(
                comment(childCommentId, articleId, authorId), 1L, now());
        writer.upsertActivity(direct, 1L, now());
        writer.upsertActivity(child, 1L, now());

        handler.project(deletionEvent(
                OutboxEventType.ARTICLE_HARD_DELETED,
                OutboxAggregateType.ARTICLE,
                articleId,
                new ArticleOutboxPayload(
                        OutboxEventAction.HARD_DELETED,
                        new ProjectionImpact(List.of(), List.of(capturedCommentId))
                )
        ), emptySource(), now());

        assertScrubbed(articleDocument(articleId), 10L, "articleId", "title", "summary");
        assertScrubbed(commentDocument(childCommentId), 10L, "commentId", "articleId");
        assertScrubbed(commentDocument(capturedCommentId), 10L, "commentId", "articleId");
        assertScrubbed(activityDocument(direct), 10L, "userId", "targetId", "type");
        assertScrubbed(activityDocument(child), 10L, "userId", "targetId", "type");
    }

    private void assertInterestHardDeleteCleanup() {
        UUID interestId = UUID.randomUUID();
        UUID subscriberId = UUID.randomUUID();
        UUID capturedSubscriberId = UUID.randomUUID();
        ActivityProjection existing = interestSubscription(subscriberId, interestId);
        ActivityProjection captured = interestSubscription(capturedSubscriberId, interestId);
        writer.upsertInterestSnapshot(interest(interestId), 1L, now());
        writer.upsertActivity(existing, 1L, now());

        handler.project(deletionEvent(
                OutboxEventType.INTEREST_HARD_DELETED,
                OutboxAggregateType.INTEREST,
                interestId,
                new InterestOutboxPayload(
                        OutboxEventAction.HARD_DELETED,
                        new ProjectionImpact(
                                List.of(new ActivityProjectionKeyPayload(
                                        capturedSubscriberId,
                                        OutboxEventType.INTEREST_SUBSCRIBED,
                                        interestId
                                )),
                                List.of()
                        )
                )
        ), emptySource(), now());

        assertScrubbed(interestDocument(interestId), 10L, "interestId", "name", "keywords");
        assertScrubbed(activityDocument(existing), 10L, "userId", "targetId", "type");
        assertScrubbed(activityDocument(captured), 10L, "userId", "targetId", "type");
    }

    private void assertUserHardDeleteCleanup() {
        UUID userId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();
        UUID authoredCommentId = UUID.randomUUID();
        UUID capturedCommentId = UUID.randomUUID();
        ActivityProjection existing = articleView(userId, articleId);
        ActivityProjection captured = interestSubscription(userId, UUID.randomUUID());
        writer.upsertActivity(existing, 1L, now());
        writer.upsertCommentSnapshot(
                comment(authoredCommentId, articleId, userId), 1L, now());

        ProjectionImpact impact = new ProjectionImpact(
                List.of(new ActivityProjectionKeyPayload(
                        userId, OutboxEventType.INTEREST_SUBSCRIBED, captured.targetId())),
                List.of(capturedCommentId)
        );
        handler.project(deletionEvent(
                OutboxEventType.USER_HARD_DELETED,
                OutboxAggregateType.USER,
                userId,
                new UserHardDeleteOutboxPayload(
                        OutboxEventAction.HARD_DELETED,
                        List.of(authoredCommentId),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        impact
                )
        ), emptySource(), now());

        assertScrubbed(activityDocument(existing), 10L, "userId", "targetId", "type");
        assertScrubbed(activityDocument(captured), 10L, "userId", "targetId", "type");
        assertScrubbed(commentDocument(authoredCommentId), 10L,
                "commentId", "content", "authorUserId");
        assertScrubbed(commentDocument(capturedCommentId), 10L,
                "commentId", "content", "authorUserId");
    }

    private OutboxEvent outboxEvent(
            OutboxEventType eventType,
            OutboxAggregateType aggregateType,
            UUID aggregateId,
            UUID actorUserId,
            OutboxEventPayload payload,
            long projectionVersion
    ) {
        OutboxEvent event = OutboxEvent.createPending(
                eventType,
                aggregateType,
                aggregateId,
                actorUserId,
                payloadSerializer.serialize(payload),
                now().minusMinutes(2),
                projectionVersion
        );
        ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        return event;
    }

    private DecodedOutboxEvent deletionEvent(
            OutboxEventType eventType,
            OutboxAggregateType aggregateType,
            UUID aggregateId,
            OutboxEventPayload payload
    ) {
        return new DecodedOutboxEvent(
                UUID.randomUUID(),
                eventType,
                aggregateType,
                aggregateId,
                null,
                payload,
                10L,
                0,
                now(),
                now()
        );
    }

    private ProjectionSourceBatch emptySource() {
        return new ProjectionSourceBatch(
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of()
        );
    }

    private ArticleState article(UUID articleId, String title) {
        return new ArticleState(
                articleId,
                title,
                "요약",
                ArticleSource.NAVER,
                "https://example.com/articles/" + articleId,
                now().minusDays(1),
                3L,
                2L,
                true,
                now()
        );
    }

    private CommentState comment(UUID commentId, UUID articleId, UUID authorId) {
        return new CommentState(
                commentId,
                articleId,
                "기사 제목",
                authorId,
                "작성자",
                "댓글 내용",
                2L,
                true,
                now().minusHours(1),
                now()
        );
    }

    private InterestState interest(UUID interestId) {
        return new InterestState(
                interestId,
                "관심사",
                List.of("키워드"),
                2L,
                now()
        );
    }

    private ActivityProjection articleView(UUID userId, UUID articleId) {
        return new ActivityProjection(
                UUID.randomUUID(),
                userId,
                ActivityHistoryType.ARTICLE_VIEWED,
                ActivityTargetType.ARTICLE,
                articleId,
                null,
                null,
                now()
        );
    }

    private ActivityProjection commentActivity(
            UUID userId,
            UUID commentId,
            UUID articleId,
            ActivityHistoryType type
    ) {
        return new ActivityProjection(
                UUID.randomUUID(),
                userId,
                type,
                ActivityTargetType.COMMENT,
                commentId,
                ActivityTargetType.ARTICLE,
                articleId,
                now()
        );
    }

    private ActivityProjection interestSubscription(UUID userId, UUID interestId) {
        return new ActivityProjection(
                UUID.randomUUID(),
                userId,
                ActivityHistoryType.INTEREST_SUBSCRIBED,
                ActivityTargetType.INTEREST,
                interestId,
                null,
                null,
                now()
        );
    }

    private Document articleDocument(UUID articleId) {
        return document(ARTICLE_SNAPSHOTS, MongoProjectionKeyFactory.article(articleId));
    }

    private Document commentDocument(UUID commentId) {
        return document(COMMENT_SNAPSHOTS, MongoProjectionKeyFactory.comment(commentId));
    }

    private Document interestDocument(UUID interestId) {
        return document(INTEREST_SNAPSHOTS, MongoProjectionKeyFactory.interest(interestId));
    }

    private Document activityDocument(ActivityProjection activity) {
        return document(
                ACTIVITY_HISTORIES,
                MongoProjectionKeyFactory.activity(
                        activity.userId(),
                        activity.type(),
                        activity.targetType(),
                        activity.targetId()
                )
        );
    }

    private Document document(String collection, String id) {
        return mongoTemplate.getCollection(collection)
                .find(Filters.eq("_id", id))
                .first();
    }

    private void assertScrubbed(Document document, long version, String... removedFields) {
        assertThat(document).isNotNull();
        assertThat(document.getLong("projectionVersion")).isEqualTo(version);
        assertThat(document.getBoolean("visible")).isFalse();
        assertThat(document.getBoolean("tombstone")).isTrue();
        assertThat(document).doesNotContainKeys(removedFields);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(CLOCK.instant(), CLOCK.getZone());
    }

    private static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException | Error e) {
            return false;
        }
    }
}
