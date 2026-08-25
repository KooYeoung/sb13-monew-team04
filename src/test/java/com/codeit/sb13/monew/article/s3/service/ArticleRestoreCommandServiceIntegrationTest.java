package com.codeit.sb13.monew.article.s3.service;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.repository.ArticleRepository;
import com.codeit.sb13.monew.article.s3.service.dto.ArticleBackupItem;
import com.codeit.sb13.monew.article.s3.service.dto.ArticleRestoreResult;
import com.codeit.sb13.monew.global.config.JpaAuditingConfig;
import com.codeit.sb13.monew.global.config.QueryDslConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({
        QueryDslConfig.class,
        JpaAuditingConfig.class,
        ArticleRestoreCommandService.class,
        ArticleRestoreCommandServiceIntegrationTest.ConflictOnceSaveServiceConfig.class
})
@ActiveProfiles("test")
@DisplayName("ArticleRestoreCommandService 통합 테스트")
class ArticleRestoreCommandServiceIntegrationTest {

    private static final LocalDate RESTORE_DATE = LocalDate.of(2026, 8, 23);
    private static final String CONFLICT_LINK = "https://example.com/news/conflict";
    private static final String NORMAL_LINK = "https://example.com/news/normal";

    @Autowired
    private ArticleRestoreCommandService commandService;

    @Autowired
    private ArticleRepository articleRepository;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("실제 unique 충돌이 발생해도 충돌 항목만 건너뛰고 정상 항목은 커밋한다")
    void skipsConflictedItemAndCommitsNormalItem() {
        ArticleBackupItem conflictItem = backupItem(CONFLICT_LINK, "충돌 복구 기사");
        ArticleBackupItem normalItem = backupItem(NORMAL_LINK, "정상 복구 기사");

        ArticleRestoreResult result = commandService.restore(RESTORE_DATE, List.of(conflictItem, normalItem));

        Article conflictArticle = articleRepository.findByLink(CONFLICT_LINK).orElseThrow();
        Article normalArticle = articleRepository.findByLink(NORMAL_LINK).orElseThrow();
        assertThat(result.restoredArticleIds()).containsExactly(normalArticle.getId());
        assertThat(result.restoredArticleCount()).isEqualTo(1L);
        assertThat(conflictArticle.getTitle()).isEqualTo("동시 저장 기사");
        assertThat(normalArticle.getTitle()).isEqualTo("정상 복구 기사");
    }

    private ArticleBackupItem backupItem(String link, String title) {
        return new ArticleBackupItem(
                UUID.randomUUID(),
                ArticleSource.NAVER,
                link,
                title,
                "복구 기사 요약",
                LocalDateTime.of(2026, 8, 23, 10, 15),
                null
        );
    }

    @TestConfiguration
    static class ConflictOnceSaveServiceConfig {

        @Bean
        ArticleRestoreSaveService articleRestoreSaveService(ArticleRepository articleRepository,
                                                            PlatformTransactionManager transactionManager) {
            return new ConflictOnceSaveService(articleRepository, transactionManager);
        }
    }

    static class ConflictOnceSaveService extends ArticleRestoreSaveService {

        private final ArticleRepository articleRepository;
        private final TransactionTemplate transactionTemplate;
        private boolean conflictInserted;

        ConflictOnceSaveService(ArticleRepository articleRepository,
                                PlatformTransactionManager transactionManager) {
            super(articleRepository);
            this.articleRepository = articleRepository;
            this.transactionTemplate = new TransactionTemplate(transactionManager);
            this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }

        @Override
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public Article save(Article article) {
            if (CONFLICT_LINK.equals(article.getLink()) && !conflictInserted) {
                conflictInserted = true;
                transactionTemplate.executeWithoutResult(status -> articleRepository.saveAndFlush(Article.create(
                        "동시 저장 기사",
                        "동시 저장 기사 요약",
                        CONFLICT_LINK,
                        LocalDateTime.of(2026, 8, 23, 9, 0),
                        ArticleSource.NAVER
                )));
            }

            return super.save(article);
        }
    }
}
