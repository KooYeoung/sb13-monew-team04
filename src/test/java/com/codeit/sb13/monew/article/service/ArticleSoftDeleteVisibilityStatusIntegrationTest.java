package com.codeit.sb13.monew.article.service;

import static com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus.ACTIVE;
import static com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus.ARTICLE_DELETED;
import static com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus.COMMENT_DELETED;
import static com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus.USER_DELETED;
import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.domain.ArticleView;
import com.codeit.sb13.monew.article.repository.ArticleRepository;
import com.codeit.sb13.monew.article.repository.ArticleViewRepository;
import com.codeit.sb13.monew.comment.domain.Comment;
import com.codeit.sb13.monew.comment.domain.CommentLike;
import com.codeit.sb13.monew.comment.repository.CommentLikeRepository;
import com.codeit.sb13.monew.comment.repository.CommentRepository;
import com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Article soft delete visibility status integration test")
class ArticleSoftDeleteVisibilityStatusIntegrationTest {

    @Autowired
    private ArticleService articleService;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ArticleViewRepository articleViewRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private CommentLikeRepository commentLikeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("softDelete marks active article activity rows as ARTICLE_DELETED")
    void softDeleteMarksActiveArticleActivityRowsAsArticleDeleted() {
        Article article = saveArticle("active-update");
        User user = saveUser("active-update");
        ArticleView articleView = articleViewRepository.save(
                ArticleView.create(article, user, LocalDateTime.now()));
        Comment comment = commentRepository.save(Comment.builder()
                .article(article)
                .user(user)
                .content("active comment")
                .build());
        CommentLike commentLike = commentLikeRepository.save(CommentLike.builder()
                .comment(comment)
                .likedBy(user)
                .build());
        flushAndClear();

        articleService.softDelete(article.getId());
        flushAndClear();

        assertThat(articleViewStatus(articleView.getId())).isEqualTo(ARTICLE_DELETED);
        assertThat(commentStatus(comment.getId())).isEqualTo(ARTICLE_DELETED);
        assertThat(commentLikeStatus(commentLike.getId())).isEqualTo(ARTICLE_DELETED);
        assertThat(articleRepository.findByIdAndDeletedAtIsNull(article.getId())).isEmpty();
        assertThat(articleViewRepository.findRecentArticleViewActivities(user.getId())).isEmpty();
        assertThat(commentRepository.findRecentCommentActivities(user.getId())).isEmpty();
        assertThat(commentLikeRepository.findRecentCommentLikeActivity(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("softDelete clears persistence context after bulk visibility status updates")
    void softDeleteClearsPersistenceContextAfterBulkVisibilityStatusUpdates() {
        Article article = saveArticle("clear-context");
        User user = saveUser("clear-context");
        ArticleView articleView = articleViewRepository.save(
                ArticleView.create(article, user, LocalDateTime.now()));
        Comment comment = commentRepository.save(Comment.builder()
                .article(article)
                .user(user)
                .content("clear context comment")
                .build());
        CommentLike commentLike = commentLikeRepository.save(CommentLike.builder()
                .comment(comment)
                .likedBy(user)
                .build());
        flushAndClear();

        ArticleView managedArticleView = articleViewRepository.findById(articleView.getId()).orElseThrow();
        Comment managedComment = commentRepository.findById(comment.getId()).orElseThrow();
        CommentLike managedCommentLike = commentLikeRepository.findById(commentLike.getId()).orElseThrow();
        assertThat(entityManager.contains(managedArticleView)).isTrue();
        assertThat(entityManager.contains(managedComment)).isTrue();
        assertThat(entityManager.contains(managedCommentLike)).isTrue();

        articleService.softDelete(article.getId());

        assertThat(entityManager.contains(managedArticleView)).isFalse();
        assertThat(entityManager.contains(managedComment)).isFalse();
        assertThat(entityManager.contains(managedCommentLike)).isFalse();
        assertThat(articleViewStatus(articleView.getId())).isEqualTo(ARTICLE_DELETED);
        assertThat(commentStatus(comment.getId())).isEqualTo(ARTICLE_DELETED);
        assertThat(commentLikeStatus(commentLike.getId())).isEqualTo(ARTICLE_DELETED);
    }

    @Test
    @DisplayName("softDelete does not overwrite non ACTIVE visibility status")
    void softDeleteDoesNotOverwriteNonActiveVisibilityStatus() {
        Article article = saveArticle("preserve-status");
        User user = saveUser("preserve-status");
        User otherUser = saveUser("preserve-status-other");
        ArticleView userDeletedView = articleViewRepository.save(
                ArticleView.create(article, user, LocalDateTime.now()));
        Comment userDeletedComment = commentRepository.save(Comment.builder()
                .article(article)
                .user(user)
                .content("user deleted comment")
                .build());
        Comment commentDeletedComment = commentRepository.save(Comment.builder()
                .article(article)
                .user(user)
                .content("comment deleted comment")
                .build());
        CommentLike userDeletedLike = commentLikeRepository.save(CommentLike.builder()
                .comment(userDeletedComment)
                .likedBy(otherUser)
                .build());
        CommentLike commentDeletedLike = commentLikeRepository.save(CommentLike.builder()
                .comment(commentDeletedComment)
                .likedBy(otherUser)
                .build());
        flushAndClear();

        setArticleViewStatus(userDeletedView.getId(), USER_DELETED);
        setCommentStatus(userDeletedComment.getId(), USER_DELETED);
        setCommentStatus(commentDeletedComment.getId(), COMMENT_DELETED);
        setCommentLikeStatus(userDeletedLike.getId(), USER_DELETED);
        setCommentLikeStatus(commentDeletedLike.getId(), COMMENT_DELETED);
        flushAndClear();

        articleService.softDelete(article.getId());
        flushAndClear();

        assertThat(articleViewStatus(userDeletedView.getId())).isEqualTo(USER_DELETED);
        assertThat(commentStatus(userDeletedComment.getId())).isEqualTo(USER_DELETED);
        assertThat(commentStatus(commentDeletedComment.getId())).isEqualTo(COMMENT_DELETED);
        assertThat(commentLikeStatus(userDeletedLike.getId())).isEqualTo(USER_DELETED);
        assertThat(commentLikeStatus(commentDeletedLike.getId())).isEqualTo(COMMENT_DELETED);
    }

    @Test
    @DisplayName("new activity rows start with ACTIVE visibility status")
    void newActivityRowsStartWithActiveVisibilityStatus() {
        Article article = saveArticle("default-status");
        User user = saveUser("default-status");
        ArticleView articleView = articleViewRepository.save(
                ArticleView.create(article, user, LocalDateTime.now()));
        Comment comment = commentRepository.save(Comment.builder()
                .article(article)
                .user(user)
                .content("default comment")
                .build());
        CommentLike commentLike = commentLikeRepository.save(CommentLike.builder()
                .comment(comment)
                .likedBy(user)
                .build());
        flushAndClear();

        assertThat(articleViewStatus(articleView.getId())).isEqualTo(ACTIVE);
        assertThat(commentStatus(comment.getId())).isEqualTo(ACTIVE);
        assertThat(commentLikeStatus(commentLike.getId())).isEqualTo(ACTIVE);
    }

    private Article saveArticle(String title) {
        return articleRepository.save(Article.create(
                title,
                "summary-" + title,
                "https://example.com/articles/" + title + "-" + UUID.randomUUID(),
                LocalDateTime.now(),
                ArticleSource.NAVER));
    }

    private User saveUser(String name) {
        return userRepository.save(User.builder()
                .email(name + "-" + UUID.randomUUID() + "@test.com")
                .nickname(name)
                .password("password")
                .build());
    }

    private void setArticleViewStatus(UUID id, ActivityVisibilityStatus status) {
        jdbcTemplate.update(
                "UPDATE article_views SET visibility_status = ? WHERE id = ?",
                status.name(), id);
    }

    private void setCommentStatus(UUID id, ActivityVisibilityStatus status) {
        jdbcTemplate.update(
                "UPDATE comments SET visibility_status = ? WHERE id = ?",
                status.name(), id);
    }

    private void setCommentLikeStatus(UUID id, ActivityVisibilityStatus status) {
        jdbcTemplate.update(
                "UPDATE comment_likes SET visibility_status = ? WHERE id = ?",
                status.name(), id);
    }

    private ActivityVisibilityStatus articleViewStatus(UUID id) {
        return articleViewRepository.findById(id).orElseThrow().getVisibilityStatus();
    }

    private ActivityVisibilityStatus commentStatus(UUID id) {
        return commentRepository.findById(id).orElseThrow().getVisibilityStatus();
    }

    private ActivityVisibilityStatus commentLikeStatus(UUID id) {
        return commentLikeRepository.findById(id).orElseThrow().getVisibilityStatus();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
