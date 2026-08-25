package com.codeit.sb13.monew.article.service;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.domain.ArticleView;
import com.codeit.sb13.monew.article.repository.ArticleRepository;
import com.codeit.sb13.monew.article.repository.ArticleViewRepository;
import com.codeit.sb13.monew.comment.domain.Comment;
import com.codeit.sb13.monew.comment.domain.CommentLike;
import com.codeit.sb13.monew.comment.repository.CommentLikeRepository;
import com.codeit.sb13.monew.comment.repository.CommentRepository;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

//기사 물리 삭제가 실제 DB에서 FK 제약 위반 없이 동작하는지 검증한다. (MID4-146)
@SpringBootTest
@DisplayName("기사 물리 삭제 통합 테스트")
class ArticleHardDeleteIntegrationTest {

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

    private UUID articleId;
    private UUID userId;
    private UUID articleViewId;
    private UUID commentId;
    private UUID commentLikeId;

    @BeforeEach
    void setUp() {
        Article article = articleRepository.save(Article.create(
                "물리 삭제 테스트 기사",
                "요약",
                "https://example.com/hard-delete-" + UUID.randomUUID(),
                LocalDateTime.now(),
                ArticleSource.NAVER));

        User user = userRepository.save(User.builder()
                .email("hard-delete-" + UUID.randomUUID() + "@example.com")
                .nickname("tester")
                .password("encoded-password")
                .build());

        ArticleView articleView = articleViewRepository.save(
                ArticleView.create(article, user, LocalDateTime.now()));

        Comment comment = commentRepository.save(Comment.builder()
                .article(article)
                .user(user)
                .content("테스트 댓글")
                .build());

        CommentLike commentLike = commentLikeRepository.save(CommentLike.builder()
                .comment(comment)
                .likedBy(user)
                .build());

        articleId = article.getId();
        userId = user.getId();
        articleViewId = articleView.getId();
        commentId = comment.getId();
        commentLikeId = commentLike.getId();
    }

    @AfterEach
    void tearDown() {
        // 이 테스트가 만든 것만 지운다. deleteAll()을 쓰면 다른 테스트 클래스가 남긴
        // 데이터까지 건드려 FK 위반으로 정리 자체가 실패할 수 있다.
        commentLikeRepository.deleteByComment_Article_Id(articleId);
        commentRepository.deleteByArticle_Id(articleId);
        articleViewRepository.deleteByArticle_Id(articleId);
        if (articleRepository.existsById(articleId)) {
            articleRepository.deleteById(articleId);
        }
        if (userRepository.existsById(userId)) {
            userRepository.deleteById(userId);
        }
    }

    @Test
    @DisplayName("기사를 물리 삭제하면 댓글, 댓글 좋아요, 조회 기록이 FK 제약 위반 없이 함께 제거된다")
    void hardDeleteRemovesAllRelatedRows() {
        // given
        assertThat(articleRepository.existsById(articleId)).isTrue();
        assertThat(articleViewRepository.existsById(articleViewId)).isTrue();
        assertThat(commentRepository.existsById(commentId)).isTrue();
        assertThat(commentLikeRepository.existsById(commentLikeId)).isTrue();

        // when
        articleService.hardDelete(articleId);

        // then
        assertThat(articleRepository.existsById(articleId)).isFalse();
        assertThat(articleViewRepository.existsById(articleViewId)).isFalse();
        assertThat(commentRepository.existsById(commentId)).isFalse();
        assertThat(commentLikeRepository.existsById(commentLikeId)).isFalse();

        // 사용자는 기사에 종속되지 않으므로 남아 있어야 한다.
        assertThat(userRepository.existsById(userId)).isTrue();
    }

    @Test
    @DisplayName("논리 삭제된 기사도 물리 삭제할 수 있다")
    void hardDeleteWorksOnSoftDeletedArticle() {
        // given
        articleService.softDelete(articleId);
        assertThat(articleRepository.findByIdAndDeletedAtIsNull(articleId)).isEmpty();

        // when
        articleService.hardDelete(articleId);

        // then
        assertThat(articleRepository.existsById(articleId)).isFalse();
        assertThat(commentRepository.existsById(commentId)).isFalse();
    }
}