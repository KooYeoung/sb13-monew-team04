package com.codeit.sb13.monew.activity.mongo.query;

import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.ACTIVITY_HISTORIES;
import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.ARTICLE_SNAPSHOTS;
import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.COMMENT_SNAPSHOTS;
import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.INTEREST_SNAPSHOTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryDocument;
import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryStatus;
import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryType;
import com.codeit.sb13.monew.activity.mongo.document.ActivityTargetType;
import com.codeit.sb13.monew.activity.mongo.document.ArticleActivitySnapshot;
import com.codeit.sb13.monew.activity.mongo.document.CommentActivitySnapshot;
import com.codeit.sb13.monew.activity.mongo.document.InterestActivitySnapshot;
import com.codeit.sb13.monew.activity.mongo.service.MongoProjectionKeyFactory;
import com.codeit.sb13.monew.activity.service.dto.RecentSubscribed;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class MongoActivityQueryReaderTest {

    @Mock
    MongoTemplate mongoTemplate;

    MongoActivityQueryReader reader;

    @BeforeEach
    void setUp() {
        reader = new MongoActivityQueryReader(mongoTemplate);
    }

    @Test
    void missingSnapshotCreatesShortPageWithoutRefillAndKeepsScanCursor() {
        UUID userId = UUID.randomUUID();
        UUID firstTarget = UUID.randomUUID();
        UUID missingTarget = UUID.randomUUID();
        UUID sentinelTarget = UUID.randomUUID();
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 4, 12, 0);
        ActivityHistoryDocument first = activity(
                "f".repeat(64), userId, firstTarget,
                ActivityHistoryType.INTEREST_SUBSCRIBED,
                ActivityTargetType.INTEREST, occurredAt
        );
        ActivityHistoryDocument missing = activity(
                "e".repeat(64), userId, missingTarget,
                ActivityHistoryType.INTEREST_SUBSCRIBED,
                ActivityTargetType.INTEREST, occurredAt.minusMinutes(1)
        );
        ActivityHistoryDocument sentinel = activity(
                "d".repeat(64), userId, sentinelTarget,
                ActivityHistoryType.INTEREST_SUBSCRIBED,
                ActivityTargetType.INTEREST, occurredAt.minusMinutes(2)
        );
        InterestActivitySnapshot firstSnapshot = new InterestActivitySnapshot(
                MongoProjectionKeyFactory.interest(firstTarget),
                firstTarget.toString(),
                "AI",
                List.of("LLM"),
                17L,
                true,
                occurredAt,
                1L,
                false
        );
        when(mongoTemplate.find(
                any(Query.class), eq(ActivityHistoryDocument.class), eq(ACTIVITY_HISTORIES)
        )).thenReturn(List.of(first, missing, sentinel));
        when(mongoTemplate.find(
                any(Query.class), eq(InterestActivitySnapshot.class), eq(INTEREST_SNAPSHOTS)
        )).thenReturn(List.of(firstSnapshot));

        ActivityReadPage<RecentSubscribed> page = reader.readSubscriptions(
                new ActivityReadRequest(userId, null, 2)
        );

        assertThat(page.content()).singleElement().satisfies(item -> {
            assertThat(item.interestId()).isEqualTo(firstTarget);
            assertThat(item.interestSubscriberCount()).isEqualTo(17L);
        });
        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextCursor()).isEqualTo(
                new ActivityReadCursor(missing.occurredAt(), missing.id())
        );
    }

    @Test
    void hiddenTombstoneAndMismatchedSnapshotsAreExcluded() {
        UUID userId = UUID.randomUUID();
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 4, 12, 30);
        UUID hiddenTarget = UUID.randomUUID();
        UUID tombstoneTarget = UUID.randomUUID();
        UUID mismatchTarget = UUID.randomUUID();
        List<ActivityHistoryDocument> activities = List.of(
                activity("f".repeat(64), userId, hiddenTarget,
                        ActivityHistoryType.INTEREST_SUBSCRIBED,
                        ActivityTargetType.INTEREST, occurredAt),
                activity("e".repeat(64), userId, tombstoneTarget,
                        ActivityHistoryType.INTEREST_SUBSCRIBED,
                        ActivityTargetType.INTEREST, occurredAt.minusMinutes(1)),
                activity("d".repeat(64), userId, mismatchTarget,
                        ActivityHistoryType.INTEREST_SUBSCRIBED,
                        ActivityTargetType.INTEREST, occurredAt.minusMinutes(2))
        );
        InterestActivitySnapshot hidden = interestSnapshot(
                hiddenTarget, hiddenTarget, false, false, occurredAt
        );
        InterestActivitySnapshot tombstone = interestSnapshot(
                tombstoneTarget, tombstoneTarget, false, true, occurredAt
        );
        InterestActivitySnapshot mismatch = interestSnapshot(
                mismatchTarget, UUID.randomUUID(), true, false, occurredAt
        );
        when(mongoTemplate.find(
                any(Query.class), eq(ActivityHistoryDocument.class), eq(ACTIVITY_HISTORIES)
        )).thenReturn(activities);
        when(mongoTemplate.find(
                any(Query.class), eq(InterestActivitySnapshot.class), eq(INTEREST_SNAPSHOTS)
        )).thenReturn(List.of(hidden, tombstone, mismatch));

        ActivityReadPage<RecentSubscribed> page = reader.readSubscriptions(
                new ActivityReadRequest(userId, null, 10)
        );

        assertThat(page.content()).isEmpty();
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void mapsAllActivityTypesToExistingDtos() {
        UUID userId = UUID.randomUUID();
        UUID interestId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 4, 13, 0);
        ActivityHistoryDocument subscription = activity(
                "a".repeat(64), userId, interestId,
                ActivityHistoryType.INTEREST_SUBSCRIBED,
                ActivityTargetType.INTEREST, occurredAt
        );
        ActivityHistoryDocument comment = activity(
                "b".repeat(64), userId, commentId,
                ActivityHistoryType.COMMENT_WRITTEN,
                ActivityTargetType.COMMENT, occurredAt
        );
        ActivityHistoryDocument commentLike = activity(
                "c".repeat(64), userId, commentId,
                ActivityHistoryType.COMMENT_LIKED,
                ActivityTargetType.COMMENT, occurredAt
        );
        ActivityHistoryDocument articleView = activity(
                "d".repeat(64), userId, articleId,
                ActivityHistoryType.ARTICLE_VIEWED,
                ActivityTargetType.ARTICLE, occurredAt
        );
        InterestActivitySnapshot interestSnapshot = new InterestActivitySnapshot(
                MongoProjectionKeyFactory.interest(interestId),
                interestId.toString(), "AI", List.of("LLM"), 11L,
                true, occurredAt, 1L, false
        );
        CommentActivitySnapshot commentSnapshot = new CommentActivitySnapshot(
                MongoProjectionKeyFactory.comment(commentId),
                commentId.toString(), articleId.toString(), "article",
                authorId.toString(), "author", "content", 7L,
                true, occurredAt.minusDays(1), occurredAt, 1L, false
        );
        ArticleActivitySnapshot articleSnapshot = new ArticleActivitySnapshot(
                MongoProjectionKeyFactory.article(articleId),
                articleId.toString(), "title", "summary", ArticleSource.NAVER,
                "https://example.com/article", occurredAt.minusDays(1),
                19L, 5L, true, occurredAt, 1L, false
        );
        when(mongoTemplate.find(
                any(Query.class), eq(ActivityHistoryDocument.class), eq(ACTIVITY_HISTORIES)
        )).thenReturn(List.of(subscription))
                .thenReturn(List.of(comment))
                .thenReturn(List.of(commentLike))
                .thenReturn(List.of(articleView));
        when(mongoTemplate.find(
                any(Query.class), eq(InterestActivitySnapshot.class), eq(INTEREST_SNAPSHOTS)
        )).thenReturn(List.of(interestSnapshot));
        when(mongoTemplate.find(
                any(Query.class), eq(CommentActivitySnapshot.class), eq(COMMENT_SNAPSHOTS)
        )).thenReturn(List.of(commentSnapshot))
                .thenReturn(List.of(commentSnapshot));
        when(mongoTemplate.find(
                any(Query.class), eq(ArticleActivitySnapshot.class), eq(ARTICLE_SNAPSHOTS)
        )).thenReturn(List.of(articleSnapshot));
        ActivityReadRequest request = new ActivityReadRequest(userId, null, 10);

        assertThat(reader.readSubscriptions(request).content()).singleElement()
                .hasFieldOrPropertyWithValue("interestSubscriberCount", 11L);
        assertThat(reader.readComments(request).content()).singleElement()
                .hasFieldOrPropertyWithValue("likeCount", 7L);
        assertThat(reader.readCommentLikes(request).content()).singleElement()
                .hasFieldOrPropertyWithValue("commentLikeCount", 7L);
        assertThat(reader.readArticleViews(request).content()).singleElement()
                .satisfies(recent -> {
                    assertThat(recent.articleViewCount()).isEqualTo(19L);
                    assertThat(recent.articleCommentCount()).isEqualTo(5L);
                });
    }

    private ActivityHistoryDocument activity(
            String id,
            UUID userId,
            UUID targetId,
            ActivityHistoryType type,
            ActivityTargetType targetType,
            LocalDateTime occurredAt
    ) {
        return new ActivityHistoryDocument(
                id,
                UUID.randomUUID().toString(),
                userId.toString(),
                type,
                targetType,
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

    private InterestActivitySnapshot interestSnapshot(
            UUID keyTargetId,
            UUID storedTargetId,
            boolean visible,
            boolean tombstone,
            LocalDateTime updatedAt
    ) {
        return new InterestActivitySnapshot(
                MongoProjectionKeyFactory.interest(keyTargetId),
                storedTargetId.toString(),
                "interest",
                List.of("keyword"),
                1L,
                visible,
                updatedAt,
                1L,
                tombstone
        );
    }
}
