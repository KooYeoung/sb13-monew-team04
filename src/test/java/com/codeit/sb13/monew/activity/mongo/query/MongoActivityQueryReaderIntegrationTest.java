package com.codeit.sb13.monew.activity.mongo.query;

import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.ACTIVITY_HISTORIES;
import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.INTEREST_SNAPSHOTS;
import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryDocument;
import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryStatus;
import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryType;
import com.codeit.sb13.monew.activity.mongo.document.ActivityTargetType;
import com.codeit.sb13.monew.activity.mongo.document.InterestActivitySnapshot;
import com.codeit.sb13.monew.activity.mongo.service.MongoProjectionKeyFactory;
import com.codeit.sb13.monew.activity.service.dto.RecentSubscribed;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
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

@DisplayName("MongoDB 활동내역 복합 cursor 통합 테스트")
class MongoActivityQueryReaderIntegrationTest {

    private static final DockerImageName MONGO_IMAGE = DockerImageName.parse("mongo:8.0");
    private static GenericContainer<?> mongo;

    private MongoClient mongoClient;
    private MongoTemplate mongoTemplate;
    private MongoActivityQueryReader reader;

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
        mongoTemplate = new MongoTemplate(mongoClient, "monew-query-" + UUID.randomUUID());
        reader = new MongoActivityQueryReader(mongoTemplate);
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
    void sameOccurredAtUsesIdAsStableDescendingCursor() {
        UUID userId = UUID.randomUUID();
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 4, 14, 0);
        List<ActivityHistoryDocument> activities = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            UUID targetId = UUID.randomUUID();
            UUID sourceId = UUID.randomUUID();
            ActivityHistoryDocument activity = activity(
                    userId, targetId, sourceId, occurredAt
            );
            activities.add(activity);
            mongoTemplate.insert(activity, ACTIVITY_HISTORIES);
            mongoTemplate.insert(snapshot(targetId, occurredAt), INTEREST_SNAPSHOTS);
        }
        activities.sort(Comparator.comparing(ActivityHistoryDocument::id).reversed());

        ActivityReadPage<RecentSubscribed> first = reader.readSubscriptions(
                new ActivityReadRequest(userId, null, 2)
        );
        ActivityReadPage<RecentSubscribed> second = reader.readSubscriptions(
                new ActivityReadRequest(userId, first.nextCursor(), 2)
        );

        assertThat(first.content()).extracting(RecentSubscribed::id)
                .containsExactly(
                        UUID.fromString(activities.get(0).sourceActivityId()),
                        UUID.fromString(activities.get(1).sourceActivityId())
                );
        assertThat(first.hasNext()).isTrue();
        assertThat(first.nextCursor()).isEqualTo(new ActivityReadCursor(
                occurredAt,
                activities.get(1).id()
        ));
        assertThat(second.content()).extracting(RecentSubscribed::id)
                .containsExactly(
                        UUID.fromString(activities.get(2).sourceActivityId()),
                        UUID.fromString(activities.get(3).sourceActivityId())
                );
        assertThat(second.hasNext()).isFalse();
        assertThat(second.nextCursor()).isNull();
    }

    private ActivityHistoryDocument activity(
            UUID userId,
            UUID targetId,
            UUID sourceId,
            LocalDateTime occurredAt
    ) {
        return new ActivityHistoryDocument(
                MongoProjectionKeyFactory.activity(
                        userId,
                        ActivityHistoryType.INTEREST_SUBSCRIBED,
                        ActivityTargetType.INTEREST,
                        targetId
                ),
                sourceId.toString(),
                userId.toString(),
                ActivityHistoryType.INTEREST_SUBSCRIBED,
                ActivityTargetType.INTEREST,
                targetId.toString(),
                null,
                null,
                occurredAt,
                true,
                ActivityHistoryStatus.ACTIVE,
                null,
                null,
                occurredAt,
                occurredAt,
                1L,
                false
        );
    }

    private InterestActivitySnapshot snapshot(UUID targetId, LocalDateTime updatedAt) {
        return new InterestActivitySnapshot(
                MongoProjectionKeyFactory.interest(targetId),
                targetId.toString(),
                "interest-" + targetId,
                List.of("keyword"),
                1L,
                true,
                updatedAt,
                1L,
                false
        );
    }

    private static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException | Error e) {
            return false;
        }
    }
}
