package com.codeit.sb13.monew.article.service;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.domain.ArticleView;
import com.codeit.sb13.monew.article.mapper.ArticleMapper;
import com.codeit.sb13.monew.article.repository.ArticleViewRepository;
import com.codeit.sb13.monew.article.service.dto.ArticleViewDto;
import com.codeit.sb13.monew.article.service.impl.ArticleViewSaveService;
import com.codeit.sb13.monew.article.service.impl.ArticleViewServiceImpl;
import com.codeit.sb13.monew.global.exception.article.ArticleNotFoundException;
import com.codeit.sb13.monew.global.exception.article.ArticleViewConflictException;
import com.codeit.sb13.monew.global.exception.user.UserNotFoundException;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ArticleViewService 단위 테스트")
class ArticleViewServiceTest {

    @Mock
    private ArticleViewRepository articleViewRepository;

    @Mock
    private ArticleService articleService;

    @InjectMocks
    private ArticleViewServiceImpl articleViewService;

    @Mock
    private UserService userService;

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private ArticleViewSaveService articleViewSaveService;

    private UUID testArticleId;
    private UUID testUserId;
    private Article testArticle;
    private User testUser;
    private ArticleViewDto testViewDto;

    @BeforeEach
    void setUp() {
        testArticleId = UUID.randomUUID();
        testUserId = UUID.randomUUID();

        testArticle = Article.create(
                "Test Article",
                "Test Summary",
                "https://example.com/article",
                LocalDateTime.now(),
                ArticleSource.NAVER
        );

        testUser = User.builder()
                .email("test@example.com")
                .nickname("tester")
                .password("encoded-password")
                .build();

        ReflectionTestUtils.setField(testArticle, "id", testArticleId);
        ReflectionTestUtils.setField(testUser, "id", testUserId);

        testViewDto = new ArticleViewDto(
                UUID.randomUUID(),
                testUserId,
                LocalDateTime.now(),
                testArticleId,
                ArticleSource.NAVER,
                "https://example.com/article",
                "Test Article",
                LocalDateTime.now(),
                "Test Summary",
                0L,
                1L
        );
    }

    @Test
    @DisplayName("기사 조회 기록 생성 - 새로운 기록")
    void testRecordViewNewRecord() {
        // given
        ArticleView savedView = ArticleView.create(testArticle, testUser, LocalDateTime.now());

        when(articleService.findById(testArticleId)).thenReturn(testArticle);
        when(userService.findById(testUserId)).thenReturn(testUser);
        // 1회차: 기존 기록 없음 -> INSERT, 2회차: 저장 결과 재조회
        when(articleViewRepository.findByArticleAndUser(testArticle, testUser))
                .thenReturn(Optional.empty(), Optional.of(savedView));
        when(articleViewRepository.countByArticleAndUser_DeletedAtIsNull(testArticle)).thenReturn(1L);
        when(articleMapper.toViewDto(savedView, 0L, 1L)).thenReturn(testViewDto);

        // when
        ArticleViewDto result = articleViewService.recordView(testArticleId, testUserId);

        // then
        assertThat(result).isEqualTo(testViewDto);
        verify(articleViewSaveService, times(1))
                .create(eq(testArticleId), eq(testUserId), any(LocalDateTime.class));
        verify(articleViewRepository, times(2)).findByArticleAndUser(testArticle, testUser);
        verify(articleViewRepository, never()).save(any(ArticleView.class));
    }

    @Test
    @DisplayName("기사 조회 기록 갱신 - viewedAt이 이전보다 최신으로 변경된다")
    void testRecordViewExistingRecord() {
        // given
        LocalDateTime previousViewedAt = LocalDateTime.now().minusDays(1);
        ArticleView existingView = ArticleView.create(testArticle, testUser, previousViewedAt);

        when(articleService.findById(testArticleId)).thenReturn(testArticle);
        when(userService.findById(testUserId)).thenReturn(testUser);
        when(articleViewRepository.findByArticleAndUser(testArticle, testUser))
                .thenReturn(Optional.of(existingView));
        when(articleViewRepository.save(any(ArticleView.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(articleViewRepository.countByArticleAndUser_DeletedAtIsNull(testArticle)).thenReturn(3L);
        when(articleMapper.toViewDto(any(ArticleView.class), eq(0L), eq(3L)))
                .thenReturn(testViewDto);

        // when
        ArticleViewDto result = articleViewService.recordView(testArticleId, testUserId);

        // then
        assertThat(result).isEqualTo(testViewDto);

        ArgumentCaptor<ArticleView> captor = ArgumentCaptor.forClass(ArticleView.class);
        verify(articleViewRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getViewedAt()).isAfter(previousViewedAt);
    }

    @Test
    @DisplayName("기사 조회 기록 생성 실패 - 기사 없음")
    void testRecordViewArticleNotFound() {
        // given
        when(articleService.findById(testArticleId))
                .thenThrow(new ArticleNotFoundException(testArticleId));

        // when & then
        assertThatThrownBy(() -> articleViewService.recordView(testArticleId, testUserId))
                .isInstanceOf(ArticleNotFoundException.class);
        verify(userService, never()).findById(any(UUID.class));
        verify(articleViewRepository, never()).save(any(ArticleView.class));
        verify(articleViewSaveService, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("기사 조회 기록 생성 실패 - 사용자 없음")
    void testRecordViewUserNotFound() {
        // given
        when(articleService.findById(testArticleId)).thenReturn(testArticle);
        when(userService.findById(testUserId))
                .thenThrow(new UserNotFoundException(testUserId));

        // when & then
        assertThatThrownBy(() -> articleViewService.recordView(testArticleId, testUserId))
                .isInstanceOf(UserNotFoundException.class);
        verify(articleViewRepository, never()).save(any(ArticleView.class));
        verify(articleViewSaveService, never()).create(any(), any(), any());
    }

    // 제약명 표기는 DB 벤더와 설정에 따라 대소문자가 달라진다.
    // (PostgreSQL은 소문자, H2는 DATABASE_TO_LOWER 설정에 따라 달라짐)
    @ParameterizedTest(name = "제약명 표기가 {0} 일 때")
    @ValueSource(strings = {"uk_article_views_article_user", "UK_ARTICLE_VIEWS_ARTICLE_USER"})
    @DisplayName("동시 요청으로 UNIQUE 제약을 위반하면 기존 조회 기록을 갱신해 정상 응답한다")
    void testRecordViewUniqueViolationRecovers(String constraintName) {
        // given
        LocalDateTime previousViewedAt = LocalDateTime.now().minusDays(1);
        ArticleView concurrentView = ArticleView.create(testArticle, testUser, previousViewedAt);

        when(articleService.findById(testArticleId)).thenReturn(testArticle);
        when(userService.findById(testUserId)).thenReturn(testUser);
        // 1회차: 기존 기록 없음 -> INSERT 시도, 2회차: 상대 트랜잭션이 커밋한 행이 보임
        when(articleViewRepository.findByArticleAndUser(testArticle, testUser))
                .thenReturn(Optional.empty(), Optional.of(concurrentView));
        doThrow(uniqueViolation(constraintName)).when(articleViewSaveService)
                .create(eq(testArticleId), eq(testUserId), any(LocalDateTime.class));
        when(articleViewRepository.save(any(ArticleView.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(articleViewRepository.countByArticleAndUser_DeletedAtIsNull(testArticle)).thenReturn(1L);
        when(articleMapper.toViewDto(concurrentView, 0L, 1L)).thenReturn(testViewDto);

        // when
        ArticleViewDto result = articleViewService.recordView(testArticleId, testUserId);

        // then
        assertThat(result).isEqualTo(testViewDto);
        assertThat(concurrentView.getViewedAt()).isAfter(previousViewedAt);
        verify(articleViewRepository, times(1)).save(concurrentView);
    }

    @Test
    @DisplayName("충돌 후 기존 조회 기록 재조회에도 실패하면 ArticleViewConflictException을 던진다")
    void testRecordViewUniqueViolationWithoutExistingRecord() {
        // given
        when(articleService.findById(testArticleId)).thenReturn(testArticle);
        when(userService.findById(testUserId)).thenReturn(testUser);
        when(articleViewRepository.findByArticleAndUser(testArticle, testUser))
                .thenReturn(Optional.empty());
        doThrow(uniqueViolation()).when(articleViewSaveService)
                .create(eq(testArticleId), eq(testUserId), any(LocalDateTime.class));

        // when & then
        assertThatThrownBy(() -> articleViewService.recordView(testArticleId, testUserId))
                .isInstanceOf(ArticleViewConflictException.class);
    }

    @Test
    @DisplayName("다른 무결성 위반은 변환하지 않고 그대로 전파한다")
    void testRecordViewOtherIntegrityViolation() {
        // given
        when(articleService.findById(testArticleId)).thenReturn(testArticle);
        when(userService.findById(testUserId)).thenReturn(testUser);
        when(articleViewRepository.findByArticleAndUser(testArticle, testUser))
                .thenReturn(Optional.empty());
        doThrow(new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("null value in column \"viewed_at\"")))
                .when(articleViewSaveService)
                .create(eq(testArticleId), eq(testUserId), any(LocalDateTime.class));

        // when & then
        assertThatThrownBy(() -> articleViewService.recordView(testArticleId, testUserId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(ArticleViewConflictException.class);
        verify(articleViewRepository, times(1)).findByArticleAndUser(testArticle, testUser);
    }

    @Test
    @DisplayName("특정 기사의 조회수 조회")
    void testGetViewCount() {
        // given
        when(articleService.findById(testArticleId)).thenReturn(testArticle);
        when(articleViewRepository.countByArticleAndUser_DeletedAtIsNull(testArticle)).thenReturn(10L);

        // when
        long count = articleViewService.getViewCount(testArticleId);

        // then
        assertThat(count).isEqualTo(10L);
        verify(articleViewRepository, times(1)).countByArticleAndUser_DeletedAtIsNull(testArticle);
    }

    @Test
    @DisplayName("조회 기록이 없을 때 조회수 0 반환")
    void testGetViewCountZero() {
        // given
        when(articleService.findById(testArticleId)).thenReturn(testArticle);
        when(articleViewRepository.countByArticleAndUser_DeletedAtIsNull(testArticle)).thenReturn(0L);

        // when & then
        assertThat(articleViewService.getViewCount(testArticleId)).isZero();
    }

    @Test
    @DisplayName("특정 사용자의 조회 기록 조회")
    void testGetUserArticleViews() {
        // given
        List<ArticleView> views = Arrays.asList(
                ArticleView.create(testArticle, testUser, LocalDateTime.now()),
                ArticleView.create(testArticle, testUser, LocalDateTime.now().minusHours(1))
        );
        when(userService.findById(testUserId)).thenReturn(testUser);
        when(articleViewRepository.findByUserOrderByViewedAtDesc(testUser)).thenReturn(views);

        // when
        List<ArticleView> result = articleViewService.getUserArticleViews(testUserId);

        // then
        assertThat(result).hasSize(2);
        verify(articleViewRepository, times(1)).findByUserOrderByViewedAtDesc(testUser);
    }

    @Test
    @DisplayName("사용자의 조회 기록이 없을 때 빈 리스트 반환")
    void testGetUserArticleViewsEmpty() {
        // given
        when(userService.findById(testUserId)).thenReturn(testUser);
        when(articleViewRepository.findByUserOrderByViewedAtDesc(testUser)).thenReturn(List.of());

        // when & then
        assertThat(articleViewService.getUserArticleViews(testUserId)).isEmpty();
    }

    @Test
    @DisplayName("사용자 조회 기록 조회 실패 - 사용자 없음")
    void testGetUserArticleViewsUserNotFound() {
        // given
        when(userService.findById(testUserId))
                .thenThrow(new UserNotFoundException(testUserId));

        // when & then
        assertThatThrownBy(() -> articleViewService.getUserArticleViews(testUserId))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("특정 기사의 조회 기록 조회")
    void testGetArticleViews() {
        // given
        List<ArticleView> views = Arrays.asList(
                ArticleView.create(testArticle, testUser, LocalDateTime.now()),
                ArticleView.create(testArticle, testUser, LocalDateTime.now().minusHours(1))
        );
        when(articleService.findById(testArticleId)).thenReturn(testArticle);
        when(articleViewRepository.findByArticleOrderByViewedAtDesc(testArticle)).thenReturn(views);

        // when & then
        assertThat(articleViewService.getArticleViews(testArticleId)).hasSize(2);
        verify(articleViewRepository, times(1)).findByArticleOrderByViewedAtDesc(testArticle);
    }

    @Test
    @DisplayName("기사의 조회 기록이 없을 때 빈 리스트 반환")
    void testGetArticleViewsEmpty() {
        // given
        when(articleService.findById(testArticleId)).thenReturn(testArticle);
        when(articleViewRepository.findByArticleOrderByViewedAtDesc(testArticle)).thenReturn(List.of());

        // when & then
        assertThat(articleViewService.getArticleViews(testArticleId)).isEmpty();
    }

    // uk_article_views_article_user 위반 상황을 재현한다. (PostgreSQL 표기)
    private DataIntegrityViolationException uniqueViolation() {
        return uniqueViolation("uk_article_views_article_user");
    }

    private DataIntegrityViolationException uniqueViolation(String constraintName) {
        return new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException(
                        "duplicate key value violates unique constraint \"" + constraintName + "\""));
    }
}