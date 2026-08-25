package com.codeit.sb13.monew.article.s3.service;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.repository.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ArticleRestoreSaveService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class ArticleRestoreSaveServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    private ArticleRestoreSaveService saveService;

    @BeforeEach
    void setUp() {
        saveService = new ArticleRestoreSaveService(articleRepository);
    }

    @Test
    @DisplayName("save()는 별도 트랜잭션으로 실행된다")
    void saveRequiresNewTransaction() throws NoSuchMethodException {
        Method saveMethod = ArticleRestoreSaveService.class.getMethod("save", Article.class);
        Transactional transactional = saveMethod.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    @DisplayName("save()는 articleRepository.saveAndFlush()에 위임한다")
    void saveDelegatesToRepository() {
        Article article = article("https://example.com/news/save-service");
        when(articleRepository.saveAndFlush(article)).thenReturn(article);

        Article result = saveService.save(article);

        assertThat(result).isSameAs(article);
        verify(articleRepository).saveAndFlush(article);
    }

    private Article article(String link) {
        return Article.create(
                "복구 기사 제목",
                "복구 기사 요약",
                link,
                LocalDateTime.of(2026, 8, 23, 10, 15),
                ArticleSource.NAVER
        );
    }
}
