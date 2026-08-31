package com.codeit.sb13.monew.user.service;

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
import com.codeit.sb13.monew.interest.domain.Interest;
import com.codeit.sb13.monew.interest.domain.Subscribe;
import com.codeit.sb13.monew.interest.repository.InterestRepository;
import com.codeit.sb13.monew.interest.repository.SubscribeRepository;
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
@DisplayName("User soft delete visibility status integration test")
class UserSoftDeleteVisibilityStatusIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ArticleViewRepository articleViewRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private CommentLikeRepository commentLikeRepository;

    @Autowired
    private SubscribeRepository subscribeRepository;

    @Autowired
    private InterestRepository interestRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("deleteUser marks active related activity rows as USER_DELETED")
    void deleteUserMarksActiveUserActivityRowsAsUserDeleted() {
        User user = saveUser("target");
        User otherUser = saveUser("other");
        Article article = saveArticle("target-article");

        Interest interest = saveInterest("target-interest");
        Subscribe subscribe = subscribeRepository.save(Subscribe.of(interest, user.getId()));

        ArticleView articleView = articleViewRepository.save(
                ArticleView.create(article, user, LocalDateTime.now()));

        Comment myComment = commentRepository.save(Comment.builder()
                .article(article)
                .user(user)
                .content("my comment")
                .build());
        Comment otherComment = commentRepository.save(Comment.builder()
                .article(article)
                .user(otherUser)
                .content("other comment")
                .build());
        CommentLike likeIPressed = commentLikeRepository.save(CommentLike.builder()
                .comment(otherComment)
                .likedBy(user)
                .build());
        CommentLike likeOnMyComment = commentLikeRepository.save(CommentLike.builder()
                .comment(myComment)
                .likedBy(otherUser)
                .build());

        ArticleView otherUserArticleView = articleViewRepository.save(
                ArticleView.create(article, otherUser, LocalDateTime.now()));
        flushAndClear();

        userService.deleteUser(user.getId());
        flushAndClear();

        assertThat(subscribeStatus(subscribe.getId())).isEqualTo(USER_DELETED);
        assertThat(articleViewStatus(articleView.getId())).isEqualTo(USER_DELETED);
        assertThat(commentStatus(myComment.getId())).isEqualTo(USER_DELETED);
        assertThat(commentLikeStatus(likeIPressed.getId())).isEqualTo(USER_DELETED);
        assertThat(commentLikeStatus(likeOnMyComment.getId())).isEqualTo(USER_DELETED);

        assertThat(userRepository.findByIdAndDeletedAtIsNull(user.getId())).isEmpty();

        assertThat(articleViewStatus(otherUserArticleView.getId())).isEqualTo(ACTIVE);
        assertThat(commentStatus(otherComment.getId())).isEqualTo(ACTIVE);
    }

    @Test
    @DisplayName("deleteUser clears persistence context after bulk visibility status updates")
    void deleteUserClearsPersistenceContextAfterBulkVisibilityStatusUpdates() {
        User user = saveUser("clear-context");
        Article article = saveArticle("clear-context-article");
        Interest interest = saveInterest("clear-context-interest");
        Subscribe subscribe = subscribeRepository.save(Subscribe.of(interest, user.getId()));
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

        Subscribe managedSubscribe = subscribeRepository.findById(subscribe.getId()).orElseThrow();
        ArticleView managedArticleView = articleViewRepository.findById(articleView.getId()).orElseThrow();
        Comment managedComment = commentRepository.findById(comment.getId()).orElseThrow();
        CommentLike managedCommentLike = commentLikeRepository.findById(commentLike.getId()).orElseThrow();
        assertThat(entityManager.contains(managedSubscribe)).isTrue();
        assertThat(entityManager.contains(managedArticleView)).isTrue();
        assertThat(entityManager.contains(managedComment)).isTrue();
        assertThat(entityManager.contains(managedCommentLike)).isTrue();

        userService.deleteUser(user.getId());

        assertThat(entityManager.contains(managedSubscribe)).isFalse();
        assertThat(entityManager.contains(managedArticleView)).isFalse();
        assertThat(entityManager.contains(managedComment)).isFalse();
        assertThat(entityManager.contains(managedCommentLike)).isFalse();
        assertThat(subscribeStatus(subscribe.getId())).isEqualTo(USER_DELETED);
        assertThat(articleViewStatus(articleView.getId())).isEqualTo(USER_DELETED);
        assertThat(commentStatus(comment.getId())).isEqualTo(USER_DELETED);
        assertThat(commentLikeStatus(commentLike.getId())).isEqualTo(USER_DELETED);
    }

    @Test
    @DisplayName("deleteUser does not overwrite non ACTIVE visibility status")
    void deleteUserDoesNotOverwriteNonActiveVisibilityStatus() {
        User user = saveUser("preserve-status");
        User otherUser = saveUser("preserve-status-other");
        Article article = saveArticle("preserve-status-article");

        Comment articleDeletedComment = commentRepository.save(Comment.builder()
                .article(article)
                .user(user)
                .content("article deleted comment")
                .build());
        Comment commentDeletedComment = commentRepository.save(Comment.builder()
                .article(article)
                .user(user)
                .content("comment deleted comment")
                .build());
        CommentLike articleDeletedLike = commentLikeRepository.save(CommentLike.builder()
                .comment(articleDeletedComment)
                .likedBy(otherUser)
                .build());
        CommentLike commentDeletedLike = commentLikeRepository.save(CommentLike.builder()
                .comment(commentDeletedComment)
                .likedBy(otherUser)
                .build());
        flushAndClear();

        setCommentStatus(articleDeletedComment.getId(), ARTICLE_DELETED);
        setCommentStatus(commentDeletedComment.getId(), COMMENT_DELETED);
        setCommentLikeStatus(articleDeletedLike.getId(), ARTICLE_DELETED);
        setCommentLikeStatus(commentDeletedLike.getId(), COMMENT_DELETED);
        flushAndClear();

        userService.deleteUser(user.getId());
        flushAndClear();

        assertThat(commentStatus(articleDeletedComment.getId())).isEqualTo(ARTICLE_DELETED);
        assertThat(commentStatus(commentDeletedComment.getId())).isEqualTo(COMMENT_DELETED);
        assertThat(commentLikeStatus(articleDeletedLike.getId())).isEqualTo(ARTICLE_DELETED);
        assertThat(commentLikeStatus(commentDeletedLike.getId())).isEqualTo(COMMENT_DELETED);
    }

    @Test
    @DisplayName("new activity rows start with ACTIVE visibility status")
    void newActivityRowsStartWithActiveVisibilityStatus() {
        User user = saveUser("default-status");
        Article article = saveArticle("default-status-article");
        Interest interest = saveInterest("default-status-interest");
        Subscribe subscribe = subscribeRepository.save(Subscribe.of(interest, user.getId()));
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

        assertThat(subscribeStatus(subscribe.getId())).isEqualTo(ACTIVE);
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

    private Interest saveInterest(String name) {
        Interest interest = Interest.create(name + "-" + UUID.randomUUID().toString().substring(0, 8));
        interest.addKeyword(name + "-keyword");
        return interestRepository.save(interest);
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

    private ActivityVisibilityStatus subscribeStatus(UUID id) {
        return subscribeRepository.findById(id).orElseThrow().getVisibilityStatus();
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
