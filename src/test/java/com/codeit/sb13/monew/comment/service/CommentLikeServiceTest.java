package com.codeit.sb13.monew.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.comment.domain.Comment;
import com.codeit.sb13.monew.comment.repository.CommentLikeRepository;
import com.codeit.sb13.monew.comment.repository.CommentRepository;
import com.codeit.sb13.monew.comment.repository.dto.CommentLikeResponseProjection;
import com.codeit.sb13.monew.comment.service.dto.CommentLikeDto;
import com.codeit.sb13.monew.comment.service.dto.CommentLikeRegisterCommand;
import com.codeit.sb13.monew.comment.service.impl.CommentLikeSaveService;
import com.codeit.sb13.monew.comment.service.impl.CommentLikeServiceImpl;
import com.codeit.sb13.monew.global.exception.comment.CommentNotFoundException;
import com.codeit.sb13.monew.global.exception.comment.CommentLikeNotFoundException;
import com.codeit.sb13.monew.global.exception.user.UserNotFoundException;
import com.codeit.sb13.monew.notification.service.NotificationService;
import com.codeit.sb13.monew.notification.service.dto.CommentLikedDto;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.repository.UserRepository;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("댓글 좋아요 서비스 - TDD")
@ExtendWith(MockitoExtension.class)
public class CommentLikeServiceTest {

  @Mock
  CommentLikeRepository commentLikeRepository;

  @Mock
  UserRepository userRepository;

  @Mock
  CommentRepository commentRepository;

  @InjectMocks
  CommentLikeServiceImpl commentLikeService;

  @Mock
  CommentLikeSaveService commentLikeSaveService;

  @Mock
  NotificationService notificationService;

  private Comment comment;
  private User likedBy;
  private User commentUser;
  private Article article;
  private LocalDateTime commentCreatedAt;
  private LocalDateTime likeCreatedAt;

  @BeforeEach
  void setUp() {
    // given
    article = Article.create("기사 제목", "기사 요약", "https://test.com/article",
        LocalDateTime.now(), ArticleSource.NAVER);
    commentUser = User.builder()
        .email("comment@test.com")
        .nickname("댓글 작성자")
        .password("Abcd!")
        .build();
    likedBy = User.builder()
        .email("like@test.com")
        .nickname("좋아요한 사용자")
        .password("Abcd!")
        .build();
    comment = Comment.builder()
        .article(article)
        .user(commentUser)
        .content("테스트 댓글")
        .build();

    // 생성 시각 기대값 고정
    commentCreatedAt = LocalDateTime.of(2026, 8, 21, 10, 11);
    likeCreatedAt = LocalDateTime.of(2026, 8, 21, 13, 20);

    ReflectionTestUtils.setField(likedBy, "id", UUID.randomUUID()); // 좋아요 요청한 사용자 객체에 id 필드 설정
    ReflectionTestUtils.setField(commentUser, "id", UUID.randomUUID()); // 댓글 작성자 객체에 id 필드 설정
    ReflectionTestUtils.setField(article, "id", UUID.randomUUID()); // 기사 객체에
    ReflectionTestUtils.setField(comment, "id", UUID.randomUUID()); // 댓글 객체에 id 필드 설정
    ReflectionTestUtils.setField(comment, "createdAt", commentCreatedAt); // 댓글 객체에 createdAt 필드 설정
  }

  @Test
  @DisplayName("댓글 좋아요 등록 성공 - GREEN")
  void 댓글_좋아요_등록_성공() {
    // given
    UUID commentLikeId = UUID.randomUUID();

    CommentLikeRegisterCommand command = new CommentLikeRegisterCommand(comment.getId(), likedBy.getId());

    CommentLikeResponseProjection responseProjection = responseProjection(commentLikeId, 1L);

    given(userRepository.findByIdAndDeletedAtIsNull(likedBy.getId())).willReturn(Optional.of(likedBy));
    given(commentRepository.findActiveById(comment.getId())).willReturn(Optional.of(comment));
    given(commentLikeRepository.findActiveResponseProjection(comment.getId(), likedBy.getId()))
        .willReturn(Optional.empty(), Optional.of(responseProjection));

    // when
    CommentLikeDto result = commentLikeService.likeComment(command);

    // then
    Assertions.assertAll(
        () -> assertThat(result.id()).isEqualTo(commentLikeId),
        () -> assertThat(result.likedBy()).isEqualTo(likedBy.getId()),
        () -> assertThat(result.createdAt()).isEqualTo(likeCreatedAt),
        () -> assertThat(result.commentId()).isEqualTo(comment.getId()),
        () -> assertThat(result.articleId()).isEqualTo(article.getId()),
        () -> assertThat(result.commentUserId()).isEqualTo(commentUser.getId()),
        () -> assertThat(result.commentUserNickname()).isEqualTo("댓글 작성자"),
        () -> assertThat(result.commentContent()).isEqualTo("테스트 댓글"),
        () -> assertThat(result.commentLikeCount()).isEqualTo(1L),
        () -> assertThat(result.commentCreatedAt()).isEqualTo(commentCreatedAt)
    );

    then(commentRepository).should(times(1)).findActiveById(comment.getId());
    then(userRepository).should(times(1)).findByIdAndDeletedAtIsNull(likedBy.getId());
    then(commentLikeRepository).should(times(2))
        .findActiveResponseProjection(comment.getId(), likedBy.getId());
    then(commentLikeSaveService).should(times(1)).create(comment.getId(), likedBy.getId());
    then(commentLikeRepository).should(never()).countActiveLikesByCommentId(any(UUID.class));
    then(notificationService).should(times(1))
            .notifyCommentLiked(new CommentLikedDto(likedBy, commentUser, comment.getId()));
  }

  @Test
  @DisplayName("중복 좋아요가 동시 발생해 UNIQUE 제약 위반 발생 시 기존 좋아요를 반환한다 - GREEN")
  void 동시_중복_좋아요로_UNIQUE_위반하면_기존_좋아요_반환() {
    // given
    CommentLikeRegisterCommand command = new CommentLikeRegisterCommand(comment.getId(), likedBy.getId());

    CommentLikeResponseProjection responseProjection = responseProjection(UUID.randomUUID(), 1L);

    DataIntegrityViolationException duplicateException =
        new DataIntegrityViolationException("UNIQUE 제약 위반 발생",
            new ConstraintViolationException(
                "UNIQUE 제약 위반 발생",
                new SQLException(),
                "uk_comment_likes_comment_liked_by"));

    given(commentRepository.findActiveById(comment.getId())).willReturn(Optional.of(comment));
    given(userRepository.findByIdAndDeletedAtIsNull(likedBy.getId())).willReturn(Optional.of(likedBy));

    given(commentLikeRepository.findActiveResponseProjection(comment.getId(), likedBy.getId()))
        .willReturn(Optional.empty(), Optional.of(responseProjection));

    doThrow(duplicateException)
        .when(commentLikeSaveService)
        .create(comment.getId(), likedBy.getId());

    // when
    CommentLikeDto result = commentLikeService.likeComment(command);

    // then
    Assertions.assertAll(
        () -> assertThat(result.id()).isEqualTo(responseProjection.id()),
        () -> assertThat(result.likedBy()).isEqualTo(likedBy.getId()),
        () -> assertThat(result.createdAt()).isEqualTo(likeCreatedAt),
        () -> assertThat(result.commentId()).isEqualTo(comment.getId()),
        () -> assertThat(result.articleId()).isEqualTo(article.getId()),
        () -> assertThat(result.commentUserId()).isEqualTo(commentUser.getId()),
        () -> assertThat(result.commentUserNickname()).isEqualTo(commentUser.getNickname()),
        () -> assertThat(result.commentContent()).isEqualTo(comment.getContent()),
        () -> assertThat(result.commentLikeCount()).isEqualTo(1L),
        () -> assertThat(result.commentCreatedAt()).isEqualTo(commentCreatedAt)
    );

    then(commentRepository).should(times(1)).findActiveById(comment.getId());
    then(userRepository).should(times(1)).findByIdAndDeletedAtIsNull(likedBy.getId());
    then(commentLikeRepository).should(times(2))
        .findActiveResponseProjection(comment.getId(), likedBy.getId());
    then(commentLikeSaveService).should(times(1)).create(comment.getId(), likedBy.getId());
    then(commentLikeRepository).should(never()).countActiveLikesByCommentId(any(UUID.class));
    then(notificationService).should(never()).notifyCommentLiked(any(CommentLikedDto.class));
  }

  @Test
  @DisplayName("복합 UNIQUE 제약 외 저장 관련 오류는 중복 좋아요로 처리하지 않는다")
  void UNIQUE_외_저장_오류는_그대로_전파한다() {
    CommentLikeRegisterCommand command = new CommentLikeRegisterCommand(comment.getId(), likedBy.getId());
    DataIntegrityViolationException foreignKeyException =
        new DataIntegrityViolationException("FK 제약 위반 발생",
            new ConstraintViolationException(
                "FK 제약 위반 발생",
                new SQLException(),
                "fk_comment_likes_comment_id"));
    // ConstraintViolationException 추가, isDuplicateCommentLike 제약 조건 위반 이름 비교식의 false 경로까지 확인

    given(commentRepository.findActiveById(comment.getId())).willReturn(Optional.of(comment));
    given(userRepository.findByIdAndDeletedAtIsNull(likedBy.getId())).willReturn(Optional.of(likedBy));
    given(commentLikeRepository.findActiveResponseProjection(comment.getId(), likedBy.getId()))
        .willReturn(Optional.empty());
    doThrow(foreignKeyException).when(commentLikeSaveService)
        .create(comment.getId(), likedBy.getId());

    assertThatThrownBy(() -> commentLikeService.likeComment(command))
        .isSameAs(foreignKeyException);

    then(commentLikeRepository).should(times(1))
        .findActiveResponseProjection(comment.getId(), likedBy.getId());
    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("이미 좋아요한 댓글에 대해 다시 좋아요 요청 시 기존 좋아요 정보 반환 - GREEN")
  void 중복_좋아요_기존_좋아요_반환() {
    // given
    CommentLikeResponseProjection responseProjection = responseProjection(UUID.randomUUID(), 1L);

    CommentLikeRegisterCommand command = new CommentLikeRegisterCommand(comment.getId(), likedBy.getId());

    given(commentRepository.findActiveById(comment.getId())).willReturn(Optional.of(comment));
    given(commentLikeRepository.findActiveResponseProjection(comment.getId(), likedBy.getId()))
        .willReturn(Optional.of(responseProjection));
    given(userRepository.findByIdAndDeletedAtIsNull(likedBy.getId())).willReturn(Optional.of(likedBy));

    // when
    CommentLikeDto result = commentLikeService.likeComment(command);

    // then
    Assertions.assertAll(
        () -> assertThat(result.id()).isEqualTo(responseProjection.id()),
        () -> assertThat(result.likedBy()).isEqualTo(likedBy.getId()),
        () -> assertThat(result.createdAt()).isEqualTo(likeCreatedAt),
        () -> assertThat(result.commentId()).isEqualTo(comment.getId()),
        () -> assertThat(result.articleId()).isEqualTo(article.getId()),
        () -> assertThat(result.commentUserId()).isEqualTo(commentUser.getId()),
        () -> assertThat(result.commentUserNickname()).isEqualTo(commentUser.getNickname()),
        () -> assertThat(result.commentContent()).isEqualTo(comment.getContent()),
        () -> assertThat(result.commentLikeCount()).isEqualTo(1L),
        () -> assertThat(result.commentCreatedAt()).isEqualTo(commentCreatedAt)
    );
    then(commentRepository).should(times(1)).findActiveById(comment.getId());
    then(userRepository).should(times(1)).findByIdAndDeletedAtIsNull(likedBy.getId());
    then(commentLikeRepository).should(times(1))
        .findActiveResponseProjection(comment.getId(), likedBy.getId());
    then(commentLikeSaveService).should(never()).create(any(UUID.class), any(UUID.class));
    then(commentLikeRepository).should(never()).countActiveLikesByCommentId(any(UUID.class));
    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("존재하지 않는 댓글에는 좋아요를 등록할 수 없다")
  void 댓글이_없으면_예외가_발생한다() {
    // given
    CommentLikeRegisterCommand command = new CommentLikeRegisterCommand(UUID.randomUUID(), likedBy.getId());
    given(commentRepository.findActiveById(command.commentId())).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> commentLikeService.likeComment(command))
        .isInstanceOf(CommentNotFoundException.class);

    then(userRepository).shouldHaveNoInteractions();
    then(commentLikeRepository).shouldHaveNoInteractions();
    then(commentLikeSaveService).shouldHaveNoInteractions();
    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("존재하지 않는 사용자는 좋아요를 등록할 수 없다")
  void 좋아요_요청자가_없으면_예외가_발생한다() {
    // given
    UUID unknownUserId = UUID.randomUUID();
    CommentLikeRegisterCommand command = new CommentLikeRegisterCommand(comment.getId(), unknownUserId);
    given(commentRepository.findActiveById(comment.getId())).willReturn(Optional.of(comment));
    given(userRepository.findByIdAndDeletedAtIsNull(unknownUserId)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> commentLikeService.likeComment(command))
        .isInstanceOf(UserNotFoundException.class);

    then(commentLikeRepository).shouldHaveNoInteractions();
    then(commentLikeSaveService).shouldHaveNoInteractions();
    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("댓글 좋아요를 취소할 수 있다")
  void 댓글_좋아요를_취소한다() {
    // given
    CommentLikeRegisterCommand command = new CommentLikeRegisterCommand(comment.getId(), likedBy.getId());
    given(commentRepository.findActiveById(comment.getId()))
        .willReturn(Optional.of(comment));
    given(userRepository.findByIdAndDeletedAtIsNull(likedBy.getId()))
        .willReturn(Optional.of(likedBy));
    given(commentLikeRepository.deleteByCommentIdAndLikedById(comment.getId(), likedBy.getId()))
        .willReturn(1L);

    // when
    commentLikeService.unlikeComment(command);

    // then
    then(commentLikeRepository).should(times(1))
        .deleteByCommentIdAndLikedById(comment.getId(), likedBy.getId());
    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("좋아요하지 않은 댓글은 취소할 수 없다")
  void 존재하지_않는_댓글_좋아요를_취소하면_예외가_발생한다() {
    // given
    CommentLikeRegisterCommand command = new CommentLikeRegisterCommand(comment.getId(), likedBy.getId());
    given(commentRepository.findActiveById(comment.getId())).willReturn(Optional.of(comment));
    given(userRepository.findByIdAndDeletedAtIsNull(likedBy.getId())).willReturn(Optional.of(likedBy));
    given(commentLikeRepository.deleteByCommentIdAndLikedById(comment.getId(), likedBy.getId()))
        .willReturn(0L);

    // when & then
    assertThatThrownBy(() -> commentLikeService.unlikeComment(command))
        .isInstanceOf(CommentLikeNotFoundException.class);
  }

  @Test
  @DisplayName("좋아요 저장은 성공했지만 알림 저장이 실패해도, 좋아요 등록 자체는 정상적으로 성공한다")
  void 알림_저장_실패해도_좋아요_등록은_성공한다() {
    // given
    UUID commentLikeId = UUID.randomUUID();
    CommentLikeRegisterCommand command = new CommentLikeRegisterCommand(comment.getId(), likedBy.getId());

    CommentLikeResponseProjection responseProjection = responseProjection(commentLikeId, 1L);

    given(userRepository.findByIdAndDeletedAtIsNull(likedBy.getId())).willReturn(Optional.of(likedBy));
    given(commentRepository.findActiveById(comment.getId())).willReturn(Optional.of(comment));
    given(commentLikeRepository.findActiveResponseProjection(comment.getId(), likedBy.getId()))
            .willReturn(Optional.empty(), Optional.of(responseProjection));

    doThrow(new DataIntegrityViolationException("알림 저장 실패 - content 길이 초과 등"))
            .when(notificationService)
            .notifyCommentLiked(any(CommentLikedDto.class));

    // when
    CommentLikeDto result = commentLikeService.likeComment(command);

    // then
    Assertions.assertAll(
            () -> assertThat(result.id()).isEqualTo(commentLikeId),
            () -> assertThat(result.likedBy()).isEqualTo(likedBy.getId()),
            () -> assertThat(result.commentId()).isEqualTo(comment.getId()),
            () -> assertThat(result.commentLikeCount()).isEqualTo(1L)
    );

    then(commentLikeSaveService).should(times(1)).create(comment.getId(), likedBy.getId());
    then(notificationService).should(times(1)).notifyCommentLiked(any(CommentLikedDto.class));
  }

  private CommentLikeResponseProjection responseProjection(UUID commentLikeId, Long likeCount) {
    return new CommentLikeResponseProjection(
        commentLikeId,
        likedBy.getId(),
        likeCreatedAt,
        comment.getId(),
        article.getId(),
        commentUser.getId(),
        commentUser.getNickname(),
        comment.getContent(),
        likeCount,
        commentCreatedAt
    );
  }
}
