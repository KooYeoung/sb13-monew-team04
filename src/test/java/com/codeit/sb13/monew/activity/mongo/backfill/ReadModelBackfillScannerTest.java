package com.codeit.sb13.monew.activity.mongo.backfill;

import static org.assertj.core.api.Assertions.assertThat;

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
@Import({QueryDslConfig.class, JpaAuditingConfig.class, ReadModelBackfillScanner.class})
class ReadModelBackfillScannerTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ReadModelBackfillScanner scanner;

    @Test
    @DisplayName("네 활동 stage는 삭제된 사용자와 대상을 초기 투영 page에서 제외한다")
    void scansOnlyCurrentlyVisibleActivities() {
        User activeUser = persistUser("active-backfill@example.com", "활성 사용자");
        User deletedUser = persistUser("deleted-backfill@example.com", "삭제 사용자");
        deletedUser.softDelete();
        Article activeArticle = persistArticle("활성 기사", "active");
        Article deletedArticle = persistArticle("삭제 기사", "deleted");
        deletedArticle.softDelete();
        Comment activeComment = persistComment(activeArticle, activeUser, "활성 댓글");
        persistComment(activeArticle, deletedUser, "삭제 사용자의 댓글");
        persistComment(deletedArticle, activeUser, "삭제 기사의 댓글");

        Interest interest = entityManager.persist(Interest.create("초기 투영 관심사"));
        Subscribe activeSubscription = entityManager.persist(
                Subscribe.of(interest, activeUser.getId()));
        entityManager.persist(Subscribe.of(interest, deletedUser.getId()));
        CommentLike activeLike = entityManager.persist(CommentLike.builder()
                .comment(activeComment)
                .likedBy(activeUser)
                .build());
        entityManager.persist(CommentLike.builder()
                .comment(activeComment)
                .likedBy(deletedUser)
                .build());
        ArticleView activeView = entityManager.persist(ArticleView.create(
                activeArticle, activeUser, LocalDateTime.of(2026, 9, 4, 9, 0)));
        entityManager.persist(ArticleView.create(
                activeArticle, deletedUser, LocalDateTime.of(2026, 9, 4, 9, 1)));
        entityManager.persist(ArticleView.create(
                deletedArticle, activeUser, LocalDateTime.of(2026, 9, 4, 9, 2)));
        entityManager.flush();
        entityManager.clear();

        assertStageContainsOnly(ReadModelBackfillStage.SUBSCRIPTION, activeSubscription.getId());
        assertStageContainsOnly(ReadModelBackfillStage.COMMENT_WRITTEN, activeComment.getId());
        assertStageContainsOnly(ReadModelBackfillStage.COMMENT_LIKED, activeLike.getId());
        assertStageContainsOnly(ReadModelBackfillStage.ARTICLE_VIEWED, activeView.getId());
    }

    private void assertStageContainsOnly(ReadModelBackfillStage stage, UUID expectedSourceId) {
        InitialProjectionPage page = scanner.scan(stage, null, null, 100, 7L);

        assertThat(page.events())
                .extracting(InitialProjectionEvent::sourceRowId)
                .containsExactly(expectedSourceId);
        assertThat(page.events())
                .extracting(InitialProjectionEvent::projectionVersion)
                .containsOnly(7L);
    }

    private User persistUser(String email, String nickname) {
        return entityManager.persist(User.builder()
                .email(email)
                .nickname(nickname)
                .password("password")
                .build());
    }

    private Article persistArticle(String title, String path) {
        return entityManager.persist(Article.create(
                title,
                "요약",
                "https://example.com/" + path,
                LocalDateTime.of(2026, 9, 4, 8, 0),
                ArticleSource.NAVER
        ));
    }

    private Comment persistComment(Article article, User user, String content) {
        return entityManager.persist(Comment.builder()
                .article(article)
                .user(user)
                .content(content)
                .build());
    }
}
