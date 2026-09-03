package com.codeit.sb13.monew.activity.outbox.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryStatus;
import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryType;
import com.codeit.sb13.monew.activity.mongo.document.ActivityTargetType;
import com.codeit.sb13.monew.activity.mongo.service.ActivityProjection;
import com.codeit.sb13.monew.activity.mongo.service.MongoReadModelWriter;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxAggregateType;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventType;
import com.codeit.sb13.monew.activity.outbox.payload.CommentLikeOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.payload.CountOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.ArticleState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.CommentState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.RelationKey;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.RelationState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.UserState;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class OutboxProjectionHandlerTest {

    private final MongoReadModelWriter writer = mock(MongoReadModelWriter.class);
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 3, 10, 0);
    private OutboxProjectionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OutboxProjectionHandler(writer);
    }

    @Test
    @DisplayName("현재 좋아요 관계와 원본이 활성 상태이면 snapshot 이후 activity를 upsert한다")
    void projectsCurrentCommentLikeState() {
        UUID userId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();
        UUID likeId = UUID.randomUUID();
        CommentState comment = comment(commentId, articleId, true);
        RelationState like = new RelationState(
                likeId, commentId, userId, true, now.minusMinutes(1)
        );
        ProjectionSourceBatch source = source(
                Map.of(userId, new UserState(userId, "사용자", true)),
                Map.of(commentId, comment),
                Map.of(new RelationKey(commentId, userId), like),
                Map.of()
        );
        DecodedOutboxEvent event = commentLikeEvent(
                OutboxEventType.COMMENT_LIKED,
                userId,
                commentId,
                articleId
        );

        handler.project(event, source, now);

        ArgumentCaptor<ActivityProjection> activityCaptor =
                ArgumentCaptor.forClass(ActivityProjection.class);
        InOrder order = inOrder(writer);
        order.verify(writer).upsertCommentSnapshot(comment, now);
        order.verify(writer).upsertActivity(activityCaptor.capture(), eq(now));
        ActivityProjection activity = activityCaptor.getValue();
        assertThat(activity.sourceActivityId()).isEqualTo(likeId);
        assertThat(activity.type()).isEqualTo(ActivityHistoryType.COMMENT_LIKED);
        assertThat(activity.targetType()).isEqualTo(ActivityTargetType.COMMENT);
        assertThat(activity.parentTargetId()).isEqualTo(articleId);
        assertThat(activity.occurredAt()).isEqualTo(like.occurredAt());
    }

    @Test
    @DisplayName("지연된 좋아요 이벤트도 현재 관계가 없으면 기존 activity를 CANCELED로 숨긴다")
    void delayedLikeConvergesToCanceledState() {
        UUID userId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();
        CommentState comment = comment(commentId, articleId, true);
        ProjectionSourceBatch source = source(
                Map.of(userId, new UserState(userId, "사용자", true)),
                Map.of(commentId, comment),
                Map.of(),
                Map.of()
        );
        DecodedOutboxEvent event = commentLikeEvent(
                OutboxEventType.COMMENT_LIKED,
                userId,
                commentId,
                articleId
        );

        handler.project(event, source, now);

        verify(writer).hideActivity(
                any(ActivityProjection.class),
                eq(ActivityHistoryStatus.CANCELED),
                eq(null),
                eq(null),
                eq(now)
        );
        verify(writer, never()).upsertActivity(any(), any());
    }

    @Test
    @DisplayName("이벤트 actor가 탈퇴 상태이면 snapshot을 최신화한 뒤 사용자 문서를 숨긴다")
    void deletedActorDoesNotRecreateActivity() {
        UUID userId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();
        CommentState comment = comment(commentId, articleId, true);
        RelationState like = new RelationState(
                UUID.randomUUID(), commentId, userId, true, now.minusMinutes(1)
        );
        ProjectionSourceBatch source = source(
                Map.of(userId, new UserState(userId, "탈퇴 사용자", false)),
                Map.of(commentId, comment),
                Map.of(new RelationKey(commentId, userId), like),
                Map.of()
        );
        DecodedOutboxEvent event = commentLikeEvent(
                OutboxEventType.COMMENT_LIKED,
                userId,
                commentId,
                articleId
        );

        handler.project(event, source, now);

        InOrder order = inOrder(writer);
        order.verify(writer).upsertCommentSnapshot(comment, now);
        order.verify(writer).hideActivitiesByUser(userId, now);
        order.verify(writer).hideCommentSnapshotsByAuthor(userId, now);
        verify(writer, never()).upsertActivity(any(), any());
    }

    @Test
    @DisplayName("댓글 source row가 없으면 payload만으로 재생성하지 않고 cleanup한다")
    void missingCommentDoesNotRecreateDocument() {
        UUID userId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();
        DecodedOutboxEvent event = commentLikeEvent(
                OutboxEventType.COMMENT_LIKED,
                userId,
                commentId,
                articleId
        );

        handler.project(event, source(Map.of(), Map.of(), Map.of(), Map.of()), now);

        verify(writer).deleteComment(commentId);
        verify(writer, never()).upsertCommentSnapshot(any(), any());
        verify(writer, never()).upsertActivity(any(), any());
    }

    @Test
    @DisplayName("카운트 이벤트는 현재 RDB 기사 count가 포함된 snapshot을 개별 반영한다")
    void countEventRefreshesCurrentArticleSnapshot() {
        UUID articleId = UUID.randomUUID();
        ArticleState article = new ArticleState(
                articleId,
                "제목",
                "요약",
                ArticleSource.NAVER,
                "https://example.com/article",
                now.minusDays(1),
                17,
                4,
                true,
                now.minusMinutes(1)
        );
        ProjectionSourceBatch source = source(
                Map.of(), Map.of(), Map.of(), Map.of(articleId, article)
        );
        DecodedOutboxEvent event = new DecodedOutboxEvent(
                UUID.randomUUID(),
                OutboxEventType.ARTICLE_VIEW_COUNT_CHANGED,
                OutboxAggregateType.ARTICLE,
                articleId,
                UUID.randomUUID(),
                new CountOutboxPayload(OutboxEventAction.COUNT_CHANGED),
                0,
                now,
                now
        );

        handler.project(event, source, now);

        verify(writer).upsertArticleSnapshot(article, now);
    }

    private DecodedOutboxEvent commentLikeEvent(
            OutboxEventType eventType,
            UUID userId,
            UUID commentId,
            UUID articleId
    ) {
        return new DecodedOutboxEvent(
                UUID.randomUUID(),
                eventType,
                OutboxAggregateType.COMMENT,
                commentId,
                userId,
                new CommentLikeOutboxPayload(articleId, OutboxEventAction.LIKED),
                0,
                now.minusMinutes(2),
                now.minusMinutes(2)
        );
    }

    private CommentState comment(UUID commentId, UUID articleId, boolean visible) {
        return new CommentState(
                commentId,
                articleId,
                "기사 제목",
                UUID.randomUUID(),
                "작성자",
                "댓글",
                3,
                visible,
                now.minusHours(1),
                now.minusMinutes(1)
        );
    }

    private ProjectionSourceBatch source(
            Map<UUID, UserState> users,
            Map<UUID, CommentState> comments,
            Map<RelationKey, RelationState> commentLikes,
            Map<UUID, ArticleState> articles
    ) {
        return new ProjectionSourceBatch(
                users,
                Map.of(),
                comments,
                articles,
                Map.of(),
                commentLikes,
                Map.of()
        );
    }
}
