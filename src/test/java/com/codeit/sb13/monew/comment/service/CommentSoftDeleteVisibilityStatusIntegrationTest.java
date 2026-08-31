package com.codeit.sb13.monew.comment.service;

import static com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus.ACTIVE;
import static com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus.ARTICLE_DELETED;
import static com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus.COMMENT_DELETED;
import static com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus.USER_DELETED;
import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.repository.ArticleRepository;
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
@DisplayName("Comment soft delete visibility status integration test")
class CommentSoftDeleteVisibilityStatusIntegrationTest {

    @Autowired
    private CommentService commentService;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private CommentLikeRepository commentLikeRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("softDelete marks active comment like rows as COMMENT_DELETED")
    void softDeleteMarksActiveCommentLikeRowsAsCommentDeleted() {
        Article article = saveArticle("target-article");
        User author = saveUser("author");
        User liker1 = saveUser("liker1");
        User liker2 = saveUser("liker2");

        Comment targetComment = saveComment(article, author, "target comment");
        CommentLike like1 = saveCommentLike(targetComment, liker1);
        CommentLike like2 = saveCommentLike(targetComment, liker2);

        // 무관한 다른 댓글의 좋아요 (영향받으면 안 됨)
        Comment otherComment = saveComment(article, author, "other comment");
        CommentLike otherLike = saveCommentLike(otherComment, liker1);
        flushAndClear();

        commentService.softDelete(targetComment.getId());
        flushAndClear();

        assertThat(commentLikeStatus(like1.getId())).isEqualTo(COMMENT_DELETED);
        assertThat(commentLikeStatus(like2.getId())).isEqualTo(COMMENT_DELETED);
        assertThat(commentRepository.findActiveById(targetComment.getId())).isEmpty();
        assertThat(commentStatus(targetComment.getId())).isEqualTo(COMMENT_DELETED);

        // 삭제되지 않은 댓글의 좋아요는 그대로 ACTIVE
        assertThat(commentLikeStatus(otherLike.getId())).isEqualTo(ACTIVE);
    }

    @Test
    @DisplayName("softDelete marks the comment itself as COMMENT_DELETED even if it was already hidden by another cause")
    void softDeleteMarksCommentItselfAsCommentDeletedEvenIfAlreadyArticleDeleted() {
        Article article = saveArticle("self-status-article");
        User author = saveUser("self-status-author");

        Comment comment = saveComment(article, author, "self status comment");
        flushAndClear();

        setCommentStatus(comment.getId(), ARTICLE_DELETED);
        flushAndClear();

        commentService.softDelete(comment.getId());
        flushAndClear();

        assertThat(commentStatus(comment.getId())).isEqualTo(COMMENT_DELETED);
    }

    @Test
    @DisplayName("softDelete clears persistence context after bulk visibility status update")
    void softDeleteClearsPersistenceContextAfterBulkVisibilityStatusUpdate() {
        Article article = saveArticle("clear-context-article");
        User author = saveUser("clear-context-author");
        User liker = saveUser("clear-context-liker");
        Comment comment = saveComment(article, author, "clear context comment");
        CommentLike commentLike = saveCommentLike(comment, liker);
        flushAndClear();

        CommentLike managedCommentLike = commentLikeRepository.findById(commentLike.getId()).orElseThrow();
        assertThat(entityManager.contains(managedCommentLike)).isTrue();

        commentService.softDelete(comment.getId());

        assertThat(entityManager.contains(managedCommentLike)).isFalse();
        assertThat(commentLikeStatus(commentLike.getId())).isEqualTo(COMMENT_DELETED);
    }

    @Test
    @DisplayName("softDelete does not overwrite non ACTIVE visibility status")
    void softDeleteDoesNotOverwriteNonActiveVisibilityStatus() {
        Article article = saveArticle("preserve-status-article");
        User author = saveUser("preserve-status-author");
        User liker1 = saveUser("preserve-status-liker1");
        User liker2 = saveUser("preserve-status-liker2");

        Comment comment = saveComment(article, author, "preserve status comment");
        CommentLike userDeletedLike = saveCommentLike(comment, liker1);
        CommentLike articleDeletedLike = saveCommentLike(comment, liker2);
        flushAndClear();

        setCommentLikeStatus(userDeletedLike.getId(), USER_DELETED);
        setCommentLikeStatus(articleDeletedLike.getId(), ARTICLE_DELETED);
        flushAndClear();

        commentService.softDelete(comment.getId());
        flushAndClear();

        assertThat(commentLikeStatus(userDeletedLike.getId())).isEqualTo(USER_DELETED);
        assertThat(commentLikeStatus(articleDeletedLike.getId())).isEqualTo(ARTICLE_DELETED);
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
                .email(name + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com")
                .nickname(name)
                .password("password")
                .build());
    }

    private Comment saveComment(Article article, User author, String content) {
        return commentRepository.save(Comment.builder()
                .article(article)
                .user(author)
                .content(content)
                .build());
    }

    private CommentLike saveCommentLike(Comment comment, User likedBy) {
        return commentLikeRepository.save(CommentLike.builder()
                .comment(comment)
                .likedBy(likedBy)
                .build());
    }

    private void setCommentLikeStatus(UUID id, ActivityVisibilityStatus status) {
        jdbcTemplate.update(
                "UPDATE comment_likes SET visibility_status = ? WHERE id = ?",
                status.name(), id);
    }

    private ActivityVisibilityStatus commentLikeStatus(UUID id) {
        return commentLikeRepository.findById(id).orElseThrow().getVisibilityStatus();
    }

    private void setCommentStatus(UUID id, ActivityVisibilityStatus status) {
        jdbcTemplate.update(
                "UPDATE comments SET visibility_status = ? WHERE id = ?",
                status.name(), id);
    }

    private ActivityVisibilityStatus commentStatus(UUID id) {
        return commentRepository.findById(id).orElseThrow().getVisibilityStatus();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}