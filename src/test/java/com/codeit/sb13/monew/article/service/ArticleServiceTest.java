package com.codeit.sb13.monew.article.service;

import com.codeit.sb13.monew.article.repository.dto.ArticleSearchCondition;
import com.codeit.sb13.monew.article.repository.dto.ArticleSearchRow;
import com.codeit.sb13.monew.article.s3.service.dto.ArticleBackupItem;
import com.codeit.sb13.monew.article.service.dto.ArticleDto;
import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.mapper.ArticleMapper;
import com.codeit.sb13.monew.article.repository.ArticleRepository;
import com.codeit.sb13.monew.article.repository.ArticleViewRepository;
import com.codeit.sb13.monew.article.service.dto.ArticleRequest;
import com.codeit.sb13.monew.article.service.dto.ArticleSearchCommand;
import com.codeit.sb13.monew.article.service.impl.ArticleServiceImpl;
import com.codeit.sb13.monew.comment.repository.CommentLikeRepository;
import com.codeit.sb13.monew.comment.repository.CommentRepository;
import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import com.codeit.sb13.monew.global.exception.article.ArticleBackupDateInvalidException;
import com.codeit.sb13.monew.global.exception.article.ArticleNotFoundException;
import com.codeit.sb13.monew.global.exception.article.ArticleDuplicateException;
import com.codeit.sb13.monew.global.exception.user.UserNotFoundException;
import com.codeit.sb13.monew.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ArticleService 단위 테스트")
class ArticleServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private ArticleViewRepository articleViewRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private CommentLikeRepository commentLikeRepository;

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private UserService userService;

    @InjectMocks
    private ArticleServiceImpl articleService;

    private Article testArticle;
    private UUID testArticleId;
    private ArticleRequest articleRequest;
    private UUID testUserId;
    private ArticleDto expectedDto;

    @BeforeEach
    void setUp() {
        testArticleId = UUID.randomUUID();
        testArticle = Article.create(
                "Test Article",
                "Test Summary",
                "https://example.com/article",
                LocalDateTime.now(),
                ArticleSource.NAVER
        );

        articleRequest = new ArticleRequest(
                "New Article",
                "New Summary",
                "https://example.com/new",
                LocalDateTime.now(),
                ArticleSource.HANKYUNG
        );

        testUserId = UUID.randomUUID();
        expectedDto = new ArticleDto(
                testArticleId, ArticleSource.NAVER, "https://example.com/article",
                "Test Article", LocalDateTime.now(), "Test Summary", 0L, 7L, true
        );
    }

    @Test
    @DisplayName("모든 활성 기사 조회 (최신순)")
    void testFindAll() {
        // given
        List<Article> articles = Arrays.asList(
                Article.create("title 1", "summary 1", "link1", LocalDateTime.now(), ArticleSource.NAVER),
                Article.create("title 2", "summary 2", "link2", LocalDateTime.now(), ArticleSource.CHOSUN)
        );
        when(articleRepository.findAllByDeletedAtIsNullOrderByDateDesc())
                .thenReturn(articles);

        // when
        List<Article> result = articleService.findAll();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("title 1");
        assertThat(result.get(1).getTitle()).isEqualTo("title 2");
        verify(articleRepository, times(1)).findAllByDeletedAtIsNullOrderByDateDesc();
    }

    @Test
    @DisplayName("ID로 기사 조회 성공")
    void testFindByIdSuccess() {
        // given
        when(articleRepository.findByIdAndDeletedAtIsNull(testArticleId))
                .thenReturn(Optional.of(testArticle));

        // when
        Article result = articleService.findById(testArticleId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Test Article");
        verify(articleRepository, times(1)).findByIdAndDeletedAtIsNull(testArticleId);
    }

    @Test
    @DisplayName("ID로 기사 조회 실패 - 기사 없음")
    void testFindByIdNotFound() {
        // given
        when(articleRepository.findByIdAndDeletedAtIsNull(testArticleId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> articleService.findById(testArticleId))
                .isInstanceOf(ArticleNotFoundException.class);
        verify(articleRepository, times(1)).findByIdAndDeletedAtIsNull(testArticleId);
    }

    @Test
    @DisplayName("엔티티 저장 성공")
    void testSaveEntitySuccess() {
        // given
        when(articleRepository.save(any(Article.class)))
                .thenReturn(testArticle);

        // when
        Article result = articleService.save(testArticle);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Test Article");
        verify(articleRepository, times(1)).save(any(Article.class));
    }

    @Test
    @DisplayName("DTO로 기사 생성 성공")
    void testCreateSuccess() {
        // given
        when(articleRepository.findByLink(articleRequest.getLink()))
                .thenReturn(Optional.empty());

        Article savedArticle = Article.create(
                articleRequest.getTitle(),
                articleRequest.getSummary(),
                articleRequest.getLink(),
                articleRequest.getDate(),
                articleRequest.getSource()
        );
        when(articleRepository.saveAndFlush(any(Article.class)))
                .thenReturn(savedArticle);

        // when
        Article result = articleService.create(articleRequest);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo(articleRequest.getTitle());
        assertThat(result.getLink()).isEqualTo(articleRequest.getLink());
        verify(articleRepository, times(1)).findByLink(articleRequest.getLink());
        verify(articleRepository, times(1)).saveAndFlush(any(Article.class));
    }

    @Test
    @DisplayName("기사 생성 실패 - 중복된 링크")
    void testCreateDuplicateLink() {
        // given
        when(articleRepository.findByLink(articleRequest.getLink()))
                .thenReturn(Optional.of(testArticle));

        // when & then
        assertThatThrownBy(() -> articleService.create(articleRequest))
                .isInstanceOf(ArticleDuplicateException.class);
        verify(articleRepository, times(1)).findByLink(articleRequest.getLink());
        verify(articleRepository, never()).saveAndFlush(any(Article.class));
    }

    @Test
    @DisplayName("기사 삭제 (논리 삭제)")
    void testSoftDeleteSuccess() {
        // given
        when(articleRepository.findByIdAndDeletedAtIsNull(testArticleId))
                .thenReturn(Optional.of(testArticle));
        when(articleRepository.save(any(Article.class)))
                .thenReturn(testArticle);

        // when
        articleService.softDelete(testArticleId);

        // then
        verify(articleRepository, times(1)).findByIdAndDeletedAtIsNull(testArticleId);
        verify(articleRepository, times(1)).save(any(Article.class));
    }

    @Test
    @DisplayName("기사 삭제 실패 - 기사 없음")
    void testSoftDeleteNotFound() {
        // given
        when(articleRepository.findByIdAndDeletedAtIsNull(testArticleId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> articleService.softDelete(testArticleId))
                .isInstanceOf(ArticleNotFoundException.class);
        verify(articleRepository, times(1)).findByIdAndDeletedAtIsNull(testArticleId);
        verify(articleRepository, never()).save(any(Article.class));
    }

    @Test
    @DisplayName("기사 물리 삭제 - 연관 데이터를 FK 제약 순서대로 제거한다")
    void testHardDeleteSuccess() {
        // given
        when(articleRepository.existsById(testArticleId)).thenReturn(true);

        // when
        articleService.hardDelete(testArticleId);

        // then
        InOrder inOrder = inOrder(
                commentLikeRepository, commentRepository, articleViewRepository, articleRepository);
        inOrder.verify(commentLikeRepository).deleteByComment_Article_Id(testArticleId);
        inOrder.verify(commentRepository).deleteByArticle_Id(testArticleId);
        inOrder.verify(articleViewRepository).deleteByArticle_Id(testArticleId);
        inOrder.verify(articleRepository).deleteById(testArticleId);
    }

    @Test
    @DisplayName("기사 물리 삭제는 논리 삭제 여부를 따지지 않는다")
    void testHardDeleteIgnoresSoftDeleteFlag() {
        // given
        when(articleRepository.existsById(testArticleId)).thenReturn(true);

        // when
        articleService.hardDelete(testArticleId);

        // then
        // deletedAt IS NULL 조건이 붙은 조회를 쓰면 논리 삭제 -> 물리 삭제 흐름이 404로 막힌다.
        verify(articleRepository, never()).findByIdAndDeletedAtIsNull(any(UUID.class));
        verify(articleRepository, times(1)).deleteById(testArticleId);
    }

    @Test
    @DisplayName("기사 물리 삭제 실패 - 기사 없음")
    void testHardDeleteNotFound() {
        // given
        when(articleRepository.existsById(testArticleId)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> articleService.hardDelete(testArticleId))
                .isInstanceOf(ArticleNotFoundException.class);

        verify(commentLikeRepository, never()).deleteByComment_Article_Id(any(UUID.class));
        verify(commentRepository, never()).deleteByArticle_Id(any(UUID.class));
        verify(articleViewRepository, never()).deleteByArticle_Id(any(UUID.class));
        verify(articleRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    @DisplayName("단건 조회 시 viewedByMe와 viewCount를 계산해 DTO로 변환한다")
    void testGetArticle() {
        // given
        when(articleRepository.findByIdAndDeletedAtIsNull(testArticleId))
                .thenReturn(Optional.of(testArticle));
        when(articleViewRepository.existsByArticle_IdAndUser_Id(testArticleId, testUserId))
                .thenReturn(true);
        when(articleViewRepository.countByArticle_IdAndUser_DeletedAtIsNull(testArticleId))
                .thenReturn(7L);
        when(articleMapper.toDto(testArticle, true, 0L, 7L))
                .thenReturn(expectedDto);

        // when
        ArticleDto result = articleService.getArticle(testArticleId, testUserId);

        // then
        assertThat(result).isEqualTo(expectedDto);
        verify(articleMapper).toDto(testArticle, true, 0L, 7L);
    }

    @Test
    @DisplayName("존재하지 않는 기사 단건 조회 시 예외가 발생한다")
    void testGetArticleNotFound() {
        when(articleRepository.findByIdAndDeletedAtIsNull(testArticleId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> articleService.getArticle(testArticleId, testUserId))
                .isInstanceOf(ArticleNotFoundException.class);
        verify(articleViewRepository, never()).countByArticle_IdAndUser_DeletedAtIsNull(any());
    }

    @Test
    @DisplayName("출처 목록은 ArticleSource enum 전체를 반환한다")
    void testGetSources() {
        assertThat(articleService.getSources())
                .containsExactly(ArticleSource.NAVER, ArticleSource.HANKYUNG,
                        ArticleSource.CHOSUN, ArticleSource.YEONHAP);
    }

    @Test
    @DisplayName("존재하지 않는 사용자가 단건 조회를 요청하면 예외가 발생한다")
    void testGetArticleUserNotFound() {
        // given
        doThrow(new UserNotFoundException(testUserId))
                .when(userService).validateExists(testUserId);

        // when & then
        assertThatThrownBy(() -> articleService.getArticle(testArticleId, testUserId))
                .isInstanceOf(UserNotFoundException.class);
        verify(articleRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    @Test
    @DisplayName("목록 조회 - 검색 조건을 리포지토리에 그대로 전달한다")
    void testSearchArticlesPassesCondition() {
        // given
        UUID userId = UUID.randomUUID();
        ArticleSearchCommand command = new ArticleSearchCommand(
                "반도체",
                List.of(ArticleSource.NAVER, ArticleSource.CHOSUN),
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 31, 0, 0),
                userId
        );
        when(articleRepository.search(any(ArticleSearchCondition.class))).thenReturn(List.of());

        // when
        articleService.searchArticles(command);

        // then
        ArgumentCaptor<ArticleSearchCondition> captor =
                ArgumentCaptor.forClass(ArticleSearchCondition.class);
        verify(articleRepository).search(captor.capture());

        ArticleSearchCondition condition = captor.getValue();
        assertThat(condition.keyword()).isEqualTo("반도체");
        assertThat(condition.sourceIn())
                .containsExactly(ArticleSource.NAVER, ArticleSource.CHOSUN);
        assertThat(condition.publishDateFrom()).isEqualTo(LocalDateTime.of(2026, 8, 1, 0, 0));
        assertThat(condition.publishDateTo()).isEqualTo(LocalDateTime.of(2026, 8, 31, 0, 0));
        assertThat(condition.requestUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("목록 조회 - 조회 결과를 ArticleDto로 변환하고 commentCount는 0으로 채운다")
    void testSearchArticlesMapsRows() {
        // given
        UUID userId = UUID.randomUUID();
        ArticleSearchCommand command =
                new ArticleSearchCommand(null, null, null, null, userId);

        Article article = Article.create("제목", "요약", "https://example.com/1",
                LocalDateTime.now(), ArticleSource.NAVER);
        ArticleSearchRow row = new ArticleSearchRow(article, 5L, true);
        ArticleDto dto = new ArticleDto(UUID.randomUUID(), ArticleSource.NAVER,
                "https://example.com/1", "제목", LocalDateTime.now(), "요약", 0L, 5L, true);

        when(articleRepository.search(any(ArticleSearchCondition.class))).thenReturn(List.of(row));
        when(articleMapper.toDto(article, true, 0L, 5L)).thenReturn(dto);

        // when
        List<ArticleDto> result = articleService.searchArticles(command);

        // then
        assertThat(result).containsExactly(dto);
        verify(articleMapper).toDto(article, true, 0L, 5L);
    }

    @Test
    @DisplayName("목록 조회 - 요청자 검증을 리포지토리 조회보다 먼저 수행한다")
    void testSearchArticlesValidatesUserFirst() {
        // given
        UUID userId = UUID.randomUUID();
        ArticleSearchCommand command =
                new ArticleSearchCommand(null, null, null, null, userId);
        when(articleRepository.search(any(ArticleSearchCondition.class))).thenReturn(List.of());

        // when
        articleService.searchArticles(command);

        // then
        InOrder inOrder = inOrder(userService, articleRepository);
        inOrder.verify(userService).validateExists(userId);
        inOrder.verify(articleRepository).search(any(ArticleSearchCondition.class));
    }

    @Test
    @DisplayName("백업 기사 조회 - LocalDate 범위를 LocalDateTime 경계로 변환하고 백업 아이템으로 매핑한다")
    void testFindArticleBackupItemsByDateRange() {
        // given
        LocalDate from = LocalDate.of(2026, 8, 23);
        LocalDate to = LocalDate.of(2026, 8, 24);
        UUID articleId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        LocalDateTime publishedAt = LocalDateTime.of(2026, 8, 23, 10, 15);
        Article article = Article.create(
                "백업 기사",
                "백업 요약",
                "https://example.com/backup",
                publishedAt,
                ArticleSource.NAVER
        );
        ReflectionTestUtils.setField(article, "id", articleId);
        when(articleRepository.findArticlesForBackup(
                LocalDateTime.of(2026, 8, 23, 0, 0),
                LocalDateTime.of(2026, 8, 24, 0, 0)
        )).thenReturn(List.of(article));

        // when
        List<ArticleBackupItem> result = articleService.findArticleBackupItemsByDateRange(from, to);

        // then
        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.originalArticleId()).isEqualTo(articleId);
            assertThat(item.title()).isEqualTo("백업 기사");
            assertThat(item.publishedAt()).isEqualTo(publishedAt);
        });
        verify(articleRepository).findArticlesForBackup(
                LocalDateTime.of(2026, 8, 23, 0, 0),
                LocalDateTime.of(2026, 8, 24, 0, 0)
        );
    }

    @Test
    @DisplayName("백업 기사 조회 - 날짜 범위가 비어 있으면 커스텀 예외가 발생한다")
    void testFindArticleBackupItemsByDateRangeRequiresDates() {
        LocalDate to = LocalDate.of(2026, 8, 24);

        assertThatThrownBy(() -> articleService.findArticleBackupItemsByDateRange(null, to))
                .isInstanceOfSatisfying(ArticleBackupDateInvalidException.class, e -> {
                    assertThat(e.getApiErrorCode()).isEqualTo(ApiErrorCode.ARTICLE_BACKUP_DATE_INVALID);
                    assertThat(e.getDetails())
                            .containsEntry("from", null)
                            .containsEntry("to", to)
                            .containsEntry("reason", "백업 조회 날짜 범위는 필수입니다.");
                });
    }

    @Test
    @DisplayName("백업 기사 조회 - 시작일이 종료일보다 이전이 아니면 커스텀 예외가 발생한다")
    void testFindArticleBackupItemsByDateRangeRequiresIncreasingRange() {
        LocalDate date = LocalDate.of(2026, 8, 23);

        assertThatThrownBy(() -> articleService.findArticleBackupItemsByDateRange(date, date))
                .isInstanceOfSatisfying(ArticleBackupDateInvalidException.class, e -> {
                    assertThat(e.getApiErrorCode()).isEqualTo(ApiErrorCode.ARTICLE_BACKUP_DATE_INVALID);
                    assertThat(e.getDetails())
                            .containsEntry("from", date)
                            .containsEntry("to", date)
                            .containsEntry("reason", "백업 조회 시작일은 종료일보다 이전이어야 합니다.");
                });
    }

}
