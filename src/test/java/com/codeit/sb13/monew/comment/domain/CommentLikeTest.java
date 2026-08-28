package com.codeit.sb13.monew.comment.domain;

import static org.assertj.core.api.Assertions.*;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus;
import com.codeit.sb13.monew.user.domain.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("댓글 좋아요 엔티티 - TDD")
public class CommentLikeTest {

  @Test
  @DisplayName("댓글 좋아요 생성 성공 - GREEN")
  void 댓글_좋아요_생성_성공() {
    // given
    Article article = Article.create("기사 제목", "기사 요약", "https://test.com/article",
        LocalDateTime.now(), ArticleSource.NAVER);
    User commentUser =new User("comment@test.com", "댓글 작성자", "Abcd!");
    User likedBy =new User("like@test.com", "좋아요한 사용자", "Abcd!");
    Comment comment=new Comment(article, commentUser, "테스트 댓글");

    // when
    CommentLike commentLike =new CommentLike(comment, likedBy);

    // then
    Assertions.assertAll(
        ()-> assertThat(commentLike.getComment()).isEqualTo(comment),
        ()-> assertThat(commentLike.getLikedBy()).isEqualTo(likedBy),
        ()-> assertThat(commentLike.getComment().getArticle()).isEqualTo(article),
        ()-> assertThat(commentLike.getComment().getUser()).isEqualTo(commentUser),
        ()-> assertThat(commentLike.getComment().getContent()).isEqualTo("테스트 댓글"),
        ()-> assertThat(commentLike.getVisibilityStatus()).isEqualTo(ActivityVisibilityStatus.ACTIVE)
    );

  }
}
