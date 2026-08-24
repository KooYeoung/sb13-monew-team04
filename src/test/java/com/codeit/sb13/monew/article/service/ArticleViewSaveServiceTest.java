package com.codeit.sb13.monew.article.service;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleView;
import com.codeit.sb13.monew.article.repository.ArticleViewRepository;
import com.codeit.sb13.monew.article.service.impl.ArticleViewSaveService;
import com.codeit.sb13.monew.user.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("ArticleViewSaveService 단위 테스트")
class ArticleViewSaveServiceTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private ArticleViewRepository articleViewRepository;

    @InjectMocks
    private ArticleViewSaveService articleViewSaveService;

    @Test
    @DisplayName("기사와 사용자 참조를 만들어 조회 기록을 즉시 저장한다")
    void createSavesArticleViewWithReferences() {
        // given
        UUID articleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LocalDateTime viewedAt = LocalDateTime.now();
        Article article = mock(Article.class);
        User user = mock(User.class);

        given(entityManager.getReference(Article.class, articleId)).willReturn(article);
        given(entityManager.getReference(User.class, userId)).willReturn(user);

        // when
        articleViewSaveService.create(articleId, userId, viewedAt);

        // then
        ArgumentCaptor<ArticleView> captor = ArgumentCaptor.forClass(ArticleView.class);
        then(articleViewRepository).should().saveAndFlush(captor.capture());
        assertThat(captor.getValue().getArticle()).isEqualTo(article);
        assertThat(captor.getValue().getUser()).isEqualTo(user);
        assertThat(captor.getValue().getViewedAt()).isEqualTo(viewedAt);
    }
}