package com.codeit.sb13.monew.article.service;

import com.codeit.sb13.monew.article.service.dto.ArticleDto;
import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.mapper.ArticleMapper;
import com.codeit.sb13.monew.article.repository.ArticleRepository;
import com.codeit.sb13.monew.article.repository.ArticleViewRepository;
import com.codeit.sb13.monew.article.service.dto.ArticleRequest;
import com.codeit.sb13.monew.article.service.impl.ArticleServiceImpl;
import com.codeit.sb13.monew.global.exception.article.ArticleNotFoundException;
import com.codeit.sb13.monew.global.exception.article.ArticleDuplicateException;
import com.codeit.sb13.monew.global.exception.user.UserNotFoundException;
import com.codeit.sb13.monew.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    @DisplayName("단건 조회 시 viewedByMe와 viewCount를 계산해 DTO로 변환한다")
    void testGetArticle() {
        // given
        when(articleRepository.findByIdAndDeletedAtIsNull(testArticleId))
                .thenReturn(Optional.of(testArticle));
        when(articleViewRepository.existsByArticle_IdAndUser_Id(testArticleId, testUserId))
                .thenReturn(true);
        when(articleViewRepository.countByArticle_Id(testArticleId))
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
        verify(articleViewRepository, never()).countByArticle_Id(any());
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


}