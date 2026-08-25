package com.codeit.sb13.monew.article.s3.service;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.repository.ArticleRepository;
import com.codeit.sb13.monew.article.s3.service.dto.ArticleBackupItem;
import com.codeit.sb13.monew.article.s3.service.dto.ArticleRestoreResult;
import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import com.codeit.sb13.monew.global.exception.article.ArticleRestoreFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ArticleRestoreCommandService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class ArticleRestoreCommandServiceTest {

    private static final LocalDate RESTORE_DATE = LocalDate.of(2026, 8, 23);
    private static final UUID ORIGINAL_ARTICLE_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID RESTORED_ARTICLE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private ArticleRestoreSaveService articleRestoreSaveService;

    private ArticleRestoreCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new ArticleRestoreCommandService(articleRepository, articleRestoreSaveService);
    }

    @Test
    @DisplayName("restore()는 날짜 단위 트랜잭션으로 실행된다")
    void restoreIsTransactional() throws NoSuchMethodException {
        Method restoreMethod = ArticleRestoreCommandService.class.getMethod("restore", LocalDate.class, List.class);

        assertThat(restoreMethod.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    @DisplayName("DB에 같은 link가 없으면 새 기사로 복구한다")
    void restoresNewArticleWhenLinkDoesNotExist() {
        ArticleBackupItem item = backupItem();
        when(articleRepository.findByLink(item.link())).thenReturn(Optional.empty());
        when(articleRestoreSaveService.save(any(Article.class))).thenAnswer(invocation -> {
            Article article = invocation.getArgument(0);
            ReflectionTestUtils.setField(article, "id", RESTORED_ARTICLE_ID);
            return article;
        });

        ArticleRestoreResult result = commandService.restore(RESTORE_DATE, List.of(item));

        assertThat(result.restoreDate()).isEqualTo(RESTORE_DATE);
        assertThat(result.restoredArticleIds()).containsExactly(RESTORED_ARTICLE_ID);
        assertThat(result.restoredArticleCount()).isEqualTo(1L);

        ArgumentCaptor<Article> articleCaptor = ArgumentCaptor.forClass(Article.class);
        verify(articleRestoreSaveService).save(articleCaptor.capture());
        Article restoredArticle = articleCaptor.getValue();
        assertThat(restoredArticle.getLink()).isEqualTo(item.link());
        assertThat(restoredArticle.getTitle()).isEqualTo(item.title());
        assertThat(restoredArticle.getSummary()).isEqualTo(item.summary());
        assertThat(restoredArticle.getDate()).isEqualTo(item.publishedAt());
        assertThat(restoredArticle.getSource()).isEqualTo(item.source());
    }

    @Test
    @DisplayName("DB에 같은 link가 있으면 복구하지 않는다")
    void skipsWhenSameLinkExists() {
        ArticleBackupItem item = backupItem();
        when(articleRepository.findByLink(item.link())).thenReturn(Optional.of(existingArticle(item.link())));

        ArticleRestoreResult result = commandService.restore(RESTORE_DATE, List.of(item));

        assertThat(result.restoredArticleIds()).isEmpty();
        assertThat(result.restoredArticleCount()).isZero();
        verify(articleRestoreSaveService, never()).save(any(Article.class));
    }

    @Test
    @DisplayName("DB에 같은 link가 논리삭제 상태로 있으면 복구하지 않는다")
    void skipsWhenSoftDeletedSameLinkExists() {
        ArticleBackupItem item = backupItem();
        Article existingArticle = existingArticle(item.link());
        existingArticle.softDelete();
        when(articleRepository.findByLink(item.link())).thenReturn(Optional.of(existingArticle));

        ArticleRestoreResult result = commandService.restore(RESTORE_DATE, List.of(item));

        assertThat(result.restoredArticleIds()).isEmpty();
        assertThat(result.restoredArticleCount()).isZero();
        verify(articleRestoreSaveService, never()).save(any(Article.class));
    }

    @Test
    @DisplayName("unique 충돌 후 같은 link가 조회되면 이미 복구된 것으로 보고 skip한다")
    void skipsWhenUniqueConflictIsResolvedByExistingArticle() {
        ArticleBackupItem item = backupItem();
        DataIntegrityViolationException cause = new DataIntegrityViolationException("duplicate link");
        when(articleRepository.findByLink(item.link()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingArticle(item.link())));
        when(articleRestoreSaveService.save(any(Article.class))).thenThrow(cause);

        ArticleRestoreResult result = commandService.restore(RESTORE_DATE, List.of(item));

        assertThat(result.restoredArticleIds()).isEmpty();
        assertThat(result.restoredArticleCount()).isZero();
    }

    @Test
    @DisplayName("unique 충돌 후에도 같은 link가 없으면 복구 실패로 처리한다")
    void throwsRestoreFailedWhenUniqueConflictIsNotResolved() {
        ArticleBackupItem item = backupItem();
        DataIntegrityViolationException cause = new DataIntegrityViolationException("duplicate link");
        when(articleRepository.findByLink(item.link()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(articleRestoreSaveService.save(any(Article.class))).thenThrow(cause);

        assertThatThrownBy(() -> commandService.restore(RESTORE_DATE, List.of(item)))
                .isInstanceOfSatisfying(ArticleRestoreFailedException.class, e -> {
                    assertThat(e.getApiErrorCode()).isEqualTo(ApiErrorCode.ARTICLE_RESTORE_FAILED);
                    assertThat(e.getCause()).isSameAs(cause);
                    assertThat(e.getDetails())
                            .containsEntry("restoreDate", RESTORE_DATE)
                            .containsEntry("cause", "DataIntegrityViolationException");
                });
    }

    private ArticleBackupItem backupItem() {
        return new ArticleBackupItem(
                ORIGINAL_ARTICLE_ID,
                ArticleSource.NAVER,
                "https://example.com/news/1",
                "복구 기사 제목",
                "복구 기사 요약",
                LocalDateTime.of(2026, 8, 23, 10, 15),
                null
        );
    }

    private Article existingArticle(String link) {
        return Article.create(
                "기존 기사 제목",
                "기존 기사 요약",
                link,
                LocalDateTime.of(2026, 8, 23, 9, 0),
                ArticleSource.NAVER
        );
    }
}
