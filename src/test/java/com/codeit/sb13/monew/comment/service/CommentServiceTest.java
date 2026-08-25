package com.codeit.sb13.monew.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.repository.ArticleRepository;
import com.codeit.sb13.monew.comment.repository.dto.CommentSearchCondition;
import com.codeit.sb13.monew.comment.repository.dto.CommentSearchProjection;
import com.codeit.sb13.monew.comment.repository.dto.CommentSearchResult;
import com.codeit.sb13.monew.comment.service.dto.CommentDto;
import com.codeit.sb13.monew.comment.domain.Comment;
import com.codeit.sb13.monew.comment.repository.CommentRepository;
import com.codeit.sb13.monew.comment.service.dto.CommentRegisterCommand;
import com.codeit.sb13.monew.comment.service.dto.CommentSearchCommand;
import com.codeit.sb13.monew.comment.service.impl.CommentServiceImpl;
import com.codeit.sb13.monew.global.dto.CursorPageResponseDto;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("댓글 서비스 - TDD")
@ExtendWith(MockitoExtension.class)
public class CommentServiceTest {

  @Mock
  CommentRepository commentRepository;

  @Mock
  UserRepository userRepository;

  @Mock
  ArticleRepository articleRepository;

  @InjectMocks
  private CommentServiceImpl commentService;

  @Test
  @DisplayName("댓글 생성 성공 - GREEN")
  void 댓글_생성_성공() {
    // given
    Article article = Article.create("기사 제목", "기사 요약", "https://test.com/article",
        LocalDateTime.now(), ArticleSource.NAVER);
    User user = User.builder()
        .email("test@test.com")
        .nickname("테스트 사용자")
        .password("Abcd!")
        .build();
    ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(article, "id", UUID.randomUUID());
    UUID commentId = UUID.randomUUID();

    CommentRegisterCommand command=new CommentRegisterCommand(article.getId(), user.getId(), "테스트 댓글");
    given(userRepository.findById(user.getId())).willReturn(java.util.Optional.of(user));
    given(articleRepository.findById(article.getId())).willReturn(java.util.Optional.of(article));
    given(commentRepository.save(any(Comment.class))).willAnswer(invocation -> {
      Comment comment = invocation.getArgument(0);
      ReflectionTestUtils.setField(comment, "id", commentId);
      return comment;
    });
    // when

    CommentDto result = commentService.create(command);
    ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
    // then
    then(commentRepository).should(times(1)).save(captor.capture());
    Comment savedComment = captor.getValue();
    Assertions.assertAll(
        () -> assertThat(savedComment.getArticle().getId()).isEqualTo(article.getId()),
        () -> assertThat(savedComment.getUser().getId()).isEqualTo(user.getId()),
        () -> assertThat(savedComment.getContent()).isEqualTo("테스트 댓글"),
        () -> assertThat(result.id()).isEqualTo(commentId),
        () -> assertThat(result.userId()).isEqualTo(user.getId()),
        () -> assertThat(result.articleId()).isEqualTo(article.getId()),
        () -> assertThat(result.userNickname()).isEqualTo("테스트 사용자"),
        () -> assertThat(result.content()).isEqualTo("테스트 댓글"),
        () -> assertThat(result.likeCount()).isEqualTo(0L),
        () -> assertThat(result.likedByMe()).isEqualTo(false)
    );
  }

  @Test
  @DisplayName("생성일 기준 내림차순으로 댓글 목록 조회 - GREEN")
  void 생성일_내림차순_기준_댓글_목록_조회() {
    // given
    UUID articleId = UUID.randomUUID();
    UUID requestUserId = UUID.randomUUID();
    UUID commentId = UUID.randomUUID();
    UUID writerId = UUID.randomUUID();
    LocalDateTime createdAt = LocalDateTime.of(2024, 6, 1, 12, 0);

    CommentSearchCommand command=new CommentSearchCommand(articleId, CommentOrderBy.CREATED_AT,
        Direction.DESC, null, null, null, 50, requestUserId);

    CommentSearchProjection projection = new CommentSearchProjection(
        commentId,
        articleId,
        writerId,
        "작성자 닉네임",
        "테스트 댓글 내용",
        3L,
        true,
        createdAt
    );

    CommentSearchResult page=new CommentSearchResult(
        List.of(projection),
        false,
        1L
    );

    given(commentRepository.search(any(CommentSearchCondition.class))).willReturn(page);

    // when
    CursorPageResponseDto<CommentDto> result = commentService.search(command);

    // then
    then(commentRepository).should(times(1)).search(argThat(condition -> condition.articleId().equals(articleId)
        && condition.orderBy().equals(CommentOrderBy.CREATED_AT)
        && condition.direction().equals(Direction.DESC)
        && condition.cursor() == null
        && condition.after() == null
        && condition.idAfter() == null
        && condition.limit() == 50
        && condition.requestUserId().equals(requestUserId)));

    Assertions.assertAll(
        () -> assertThat(result.content()).hasSize(1),
        () -> assertThat(result.content().get(0).id()).isEqualTo(commentId),
        () -> assertThat(result.content().get(0).likeCount()).isEqualTo(3L),
        () -> assertThat(result.content().get(0).likedByMe()).isTrue(),
        () -> assertThat(result.nextCursor()).isEqualTo(createdAt.toString()),
        ()->assertThat(result.nextAfter()).isEqualTo(createdAt.toString()),
        ()->assertThat(result.nextIdAfter()).isEqualTo(commentId.toString()),
        ()->assertThat(result.size()).isEqualTo(1),
        ()->assertThat(result.totalElements()).isEqualTo(1L),
        () -> assertThat(result.hasNext()).isFalse()
    );
  }


  @Test
  @DisplayName("생성일 기준 오름차순으로 댓글 목록 조회 - GREEN")
  void 생성일_오름차순_기준_댓글_목록_조회() {
    // given
    UUID articleId = UUID.randomUUID();
    UUID requestUserId = UUID.randomUUID();

    CommentSearchCommand command=new CommentSearchCommand(articleId, CommentOrderBy.CREATED_AT,
        Direction.ASC, null, null, null, 50, requestUserId);

    given(commentRepository.search(any(CommentSearchCondition.class))).willReturn(new CommentSearchResult(List.of(), false, 0L));

    // when
    CursorPageResponseDto<CommentDto> result = commentService.search(command);

    // then
    then(commentRepository).should().search(argThat(condition -> condition.orderBy()==CommentOrderBy.CREATED_AT
        && condition.direction().equals(Direction.ASC)));

    Assertions.assertAll(
        () -> assertThat(result.content()).isEmpty(),
        () -> assertThat(result.nextCursor()).isNull(),
        ()->assertThat(result.nextAfter()).isNull(),
        ()->assertThat(result.nextIdAfter()).isNull(),
        ()->assertThat(result.size()).isZero(),
        ()->assertThat(result.totalElements()).isZero(),
        () -> assertThat(result.hasNext()).isFalse()
    );
  }


  @Test
  @DisplayName("좋아요 수 기준 오름차순으로 댓글 목록 조회 - GREEN")
  void 좋아요_오름차순_기준_댓글_목록_조회() {
    // given
    UUID articleId = UUID.randomUUID();
    UUID requestUserId = UUID.randomUUID();
    UUID commentId = UUID.randomUUID();
    UUID writerId = UUID.randomUUID();
    LocalDateTime createdAt = LocalDateTime.of(2024, 6, 1, 12, 0);

    CommentSearchCommand command=new CommentSearchCommand(articleId, CommentOrderBy.LIKE_COUNT,
        Direction.ASC, null, null, null, 50, requestUserId);

    CommentSearchProjection first = new CommentSearchProjection(
        commentId,
        articleId,
        writerId,
        "작성자 닉네임",
        "비인기 댓글",
        1L, // 비인기  댓글 좋아요 수 가정
        false,
        createdAt
    );
    UUID popularCommentId = UUID.randomUUID();
    CommentSearchProjection second = new CommentSearchProjection(
        popularCommentId,
        articleId,
        writerId,
        "작성자 닉네임",
        "인기 댓글",
        100L, // 인기 댓글 좋아요 수 가정
        true,
        createdAt.plusMinutes(5)
    );

    CommentSearchResult searchResult=new CommentSearchResult(
        List.of(first, second),
        true,
        10L
    );

    given(commentRepository.search(any(CommentSearchCondition.class))).willReturn(searchResult);

    // when
    CursorPageResponseDto<CommentDto> result = commentService.search(command);

    // then
    Assertions.assertAll(
        () -> assertThat(result.content()).hasSize(2),
        () -> assertThat(result.nextCursor()).isEqualTo("100"),
        ()->assertThat(result.nextAfter()).isEqualTo(createdAt.plusMinutes(5).toString()),
        ()->assertThat(result.nextIdAfter()).isEqualTo(popularCommentId.toString()),
        ()->assertThat(result.size()).isEqualTo(2),
        ()->assertThat(result.totalElements()).isEqualTo(10L),
        () -> assertThat(result.hasNext()).isTrue()
    );

    then(commentRepository).should(times(1)).search(argThat(condition -> condition.articleId().equals(articleId)
        && condition.orderBy().equals(CommentOrderBy.LIKE_COUNT)
        && condition.direction().equals(Direction.ASC)
        && condition.cursor() == null
        && condition.after() == null
        && condition.idAfter() == null
        && condition.limit() == 50
        && condition.requestUserId().equals(requestUserId)));
  }
}
