package com.codeit.sb13.monew.activity.outbox.worker.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxAggregateType;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventType;
import com.codeit.sb13.monew.activity.outbox.payload.ArticleOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.payload.CommentLikeOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.payload.InterestOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.worker.DecodedOutboxEvent;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.RelationKey;
import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.domain.ArticleView;
import com.codeit.sb13.monew.comment.domain.Comment;
import com.codeit.sb13.monew.comment.domain.CommentLike;
import com.codeit.sb13.monew.global.config.JpaAuditingConfig;
import com.codeit.sb13.monew.global.config.QueryDslConfig;
import com.codeit.sb13.monew.interest.domain.Interest;
import com.codeit.sb13.monew.interest.domain.Subscribe;
import com.codeit.sb13.monew.user.domain.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({QueryDslConfig.class, JpaAuditingConfig.class, OutboxProjectionSourceReader.class})
class OutboxProjectionSourceReaderTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OutboxProjectionSourceReader sourceReader;

    @Test
    @DisplayName("batch 대상의 원본, 관계, 표시값과 count를 현재 RDB 상태로 함께 조회한다")
    void readsCurrentProjectionStateInBatch() {
        User user = entityManager.persist(User.builder()
                .email("worker-source@example.com")
                .nickname("작성자")
                .password("password")
                .build());
        Article article = entityManager.persist(Article.create(
                "기사 제목",
                "기사 요약",
                "https://example.com/article",
                LocalDateTime.of(2026, 9, 3, 8, 0),
                ArticleSource.NAVER
        ));
        Comment comment = entityManager.persist(Comment.builder()
                .article(article)
                .user(user)
                .content("댓글 내용")
                .build());
        CommentLike commentLike = entityManager.persist(CommentLike.builder()
                .comment(comment)
                .likedBy(user)
                .build());
        ArticleView articleView = entityManager.persist(ArticleView.create(
                article,
                user,
                LocalDateTime.of(2026, 9, 3, 9, 0)
        ));
        Interest interest = Interest.create("AI");
        interest.addKeyword("인공지능");
        entityManager.persist(interest);
        Subscribe subscribe = entityManager.persist(Subscribe.of(interest, user.getId()));
        entityManager.flush();

        List<DecodedOutboxEvent> events = List.of(
                event(
                        OutboxEventType.COMMENT_LIKED,
                        OutboxAggregateType.COMMENT,
                        comment.getId(),
                        user.getId(),
                        new CommentLikeOutboxPayload(article.getId(), OutboxEventAction.LIKED)
                ),
                event(
                        OutboxEventType.ARTICLE_VIEWED,
                        OutboxAggregateType.ARTICLE,
                        article.getId(),
                        user.getId(),
                        new ArticleOutboxPayload(OutboxEventAction.VIEWED)
                ),
                event(
                        OutboxEventType.INTEREST_SUBSCRIBED,
                        OutboxAggregateType.INTEREST,
                        interest.getId(),
                        user.getId(),
                        new InterestOutboxPayload(OutboxEventAction.SUBSCRIBED)
                )
        );

        ProjectionSourceBatch source = sourceReader.read(events);

        assertThat(source.users().get(user.getId()).active()).isTrue();
        assertThat(source.comments().get(comment.getId()).likeCount()).isEqualTo(1);
        assertThat(source.comments().get(comment.getId()).articleTitle()).isEqualTo("기사 제목");
        assertThat(source.comments().get(comment.getId()).authorNickname()).isEqualTo("작성자");
        assertThat(source.articles().get(article.getId()).viewCount()).isEqualTo(1);
        assertThat(source.articles().get(article.getId()).commentCount()).isEqualTo(1);
        assertThat(source.interests().get(interest.getId()).keywords()).containsExactly("인공지능");
        assertThat(source.interests().get(interest.getId()).subscriberCount()).isEqualTo(1);
        assertThat(source.commentLikes().get(new RelationKey(comment.getId(), user.getId())).id())
                .isEqualTo(commentLike.getId());
        assertThat(source.articleViews().get(new RelationKey(article.getId(), user.getId())).id())
                .isEqualTo(articleView.getId());
        assertThat(source.subscriptions().get(new RelationKey(interest.getId(), user.getId())).id())
                .isEqualTo(subscribe.getId());
    }

    private DecodedOutboxEvent event(
            OutboxEventType eventType,
            OutboxAggregateType aggregateType,
            UUID aggregateId,
            UUID actorUserId,
            com.codeit.sb13.monew.activity.outbox.payload.OutboxEventPayload payload
    ) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 3, 10, 0);
        return new DecodedOutboxEvent(
                UUID.randomUUID(),
                eventType,
                aggregateType,
                aggregateId,
                actorUserId,
                payload,
                1L,
                0,
                now,
                now
        );
    }
}
