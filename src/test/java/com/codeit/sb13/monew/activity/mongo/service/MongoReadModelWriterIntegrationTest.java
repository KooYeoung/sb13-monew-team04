package com.codeit.sb13.monew.activity.mongo.service;

import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.ACTIVITY_HISTORIES;
import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.ARTICLE_SNAPSHOTS;
import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryStatus;
import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryType;
import com.codeit.sb13.monew.activity.mongo.document.ActivityTargetType;
import com.codeit.sb13.monew.activity.mongo.document.QActivityHistoryDocument;
import com.codeit.sb13.monew.activity.mongo.querydsl.MongoQuerydslSupport;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.ArticleState;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.Filters;
import java.time.LocalDateTime;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@DisplayName("Mongo Read Model projection version CAS 통합 테스트")
class MongoReadModelWriterIntegrationTest {

    private static final DockerImageName MONGO_IMAGE = DockerImageName.parse("mongo:8.0");
    private static GenericContainer<?> mongo;

    private MongoClient mongoClient;
    private MongoTemplate mongoTemplate;
    private MongoQuerydslSupport querydsl;
    private MongoReadModelWriter writer;

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
        mongoTemplate = new MongoTemplate(mongoClient, "monew-cas-" + UUID.randomUUID());
        querydsl = new MongoQuerydslSupport(mongoTemplate);
        writer = new MongoReadModelWriter(mongoTemplate, querydsl);
    }

    @Test
    void querydslPreservesIdMissingVersionAndPartialIndexFilters() {
        QActivityHistoryDocument activity =
                QActivityHistoryDocument.activityHistoryDocument;
        String id = "a".repeat(64);

        Document casFilter = querydsl.toDocument(
                activity,
                ACTIVITY_HISTORIES,
                activity.id.eq(id).and(
                        activity.projectionVersion.isNull()
                                .or(activity.projectionVersion.lt(7L))
                )
        );
        Document partialFilter = querydsl.toDocument(
                activity,
                ACTIVITY_HISTORIES,
                activity.tombstone.isFalse()
        );

        assertThat(casFilter.getString("_id")).isEqualTo(id);
        assertThat(casFilter.toJson())
                .contains("projectionVersion", "$exists", "false", "$lt");
        assertThat(partialFilter).isEqualTo(new Document("tombstone", false));
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

    @Test
    @DisplayName("V2가 먼저 반영되면 뒤늦은 V1 snapshot이 최신 값을 덮지 않는다")
    void newerSnapshotWinsWhenEventsArriveOutOfOrder() {
        UUID articleId = UUID.randomUUID();
        writer.upsertArticleSnapshot(article(articleId, "V2 제목"), 2L, now());
        writer.upsertArticleSnapshot(article(articleId, "V1 제목"), 1L, now().minusMinutes(1));

        Document stored = articleDocument(articleId);
        assertThat(stored.getString("title")).isEqualTo("V2 제목");
        assertThat(stored.getLong("projectionVersion")).isEqualTo(2L);
    }

    @Test
    @DisplayName("취소 문서가 없어도 hidden guard를 만들고 과거 활성 이벤트를 차단한다")
    void cancellationMaterializesGuardAndRejectsOlderLiveWrite() {
        ActivityProjection activity = activity();
        writer.hideActivity(activity, ActivityHistoryStatus.CANCELED, null, null, 2L, now());
        writer.upsertActivity(activity, 1L, now().minusMinutes(1));

        Document stored = activityDocument(activity);
        assertThat(stored.getBoolean("visible")).isFalse();
        assertThat(stored.getString("status")).isEqualTo(ActivityHistoryStatus.CANCELED.name());
        assertThat(stored.getLong("projectionVersion")).isEqualTo(2L);
        assertThat(stored.getBoolean("tombstone")).isFalse();
    }

    @Test
    @DisplayName("물리삭제 tombstone은 식별 필드를 제거하고 과거 재삽입을 차단한다")
    void scrubbedTombstoneRejectsOlderWrite() {
        UUID articleId = UUID.randomUUID();
        writer.upsertArticleSnapshot(article(articleId, "기존 제목"), 1L, now());
        writer.tombstoneArticle(articleId, 3L, now().plusMinutes(1));
        writer.upsertArticleSnapshot(article(articleId, "지연 제목"), 2L, now());

        Document stored = articleDocument(articleId);
        assertThat(stored.getLong("projectionVersion")).isEqualTo(3L);
        assertThat(stored.getBoolean("tombstone")).isTrue();
        assertThat(stored.getBoolean("visible")).isFalse();
        assertThat(stored).doesNotContainKeys("articleId", "title", "summary", "sourceUrl");
    }

    @Test
    @DisplayName("동일 버전 재시도는 성공 no-op이고 빠진 다른 문서는 이어서 반영할 수 있다")
    void sameVersionRetryIsNoOpAndCanCompleteAnotherDocument() {
        UUID articleId = UUID.randomUUID();
        ActivityProjection activity = activity();
        writer.upsertArticleSnapshot(article(articleId, "최초"), 5L, now());

        writer.upsertArticleSnapshot(article(articleId, "재시도 변경값"), 5L, now());
        writer.upsertActivity(activity, 5L, now());

        assertThat(articleDocument(articleId).getString("title")).isEqualTo("최초");
        assertThat(activityDocument(activity).getLong("projectionVersion")).isEqualTo(5L);
    }

    @Test
    @DisplayName("삭제 fan-out은 없는 key를 tombstone으로 만들고 기존 누락 문서도 bulk fencing한다")
    void deletionCoversAbsentImpactKeyAndExistingDocumentMissedByPayload() {
        UUID userId = UUID.randomUUID();
        ActivityProjection capturedKeyWithoutDocument = activity(userId);
        ActivityProjection existingButMissedKey = activity(userId);
        writer.upsertActivity(existingButMissedKey, 1L, now());

        writer.tombstoneActivity(capturedKeyWithoutDocument, 3L, now().plusMinutes(1));
        writer.tombstoneActivitiesByUser(userId, 3L, now().plusMinutes(1));
        writer.upsertActivity(capturedKeyWithoutDocument, 2L, now());
        writer.upsertActivity(existingButMissedKey, 2L, now());

        assertThat(activityDocument(capturedKeyWithoutDocument).getBoolean("tombstone")).isTrue();
        assertThat(activityDocument(existingButMissedKey).getBoolean("tombstone")).isTrue();
        assertThat(activityDocument(existingButMissedKey).getLong("projectionVersion")).isEqualTo(3L);
    }

    private ArticleState article(UUID id, String title) {
        return new ArticleState(
                id, title, "요약", ArticleSource.NAVER, "https://example.com/" + id,
                now().minusDays(1), 10L, 3L, true, now());
    }

    private ActivityProjection activity() {
        return activity(UUID.randomUUID());
    }

    private ActivityProjection activity(UUID userId) {
        UUID commentId = UUID.randomUUID();
        return new ActivityProjection(
                UUID.randomUUID(), userId, ActivityHistoryType.COMMENT_LIKED,
                ActivityTargetType.COMMENT, commentId, ActivityTargetType.ARTICLE,
                UUID.randomUUID(), now());
    }

    private Document articleDocument(UUID articleId) {
        return mongoTemplate.getCollection(ARTICLE_SNAPSHOTS)
                .find(Filters.eq("_id", MongoProjectionKeyFactory.article(articleId)))
                .first();
    }

    private Document activityDocument(ActivityProjection activity) {
        String id = MongoProjectionKeyFactory.activity(
                activity.userId(), activity.type(), activity.targetType(), activity.targetId());
        return mongoTemplate.getCollection(ACTIVITY_HISTORIES)
                .find(Filters.eq("_id", id))
                .first();
    }

    private LocalDateTime now() {
        return LocalDateTime.of(2026, 9, 3, 10, 0);
    }

    private static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException | Error e) {
            return false;
        }
    }
}
