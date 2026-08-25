package com.codeit.sb13.monew.article.schedule;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.schedule.config.ArticleCollectScheduleProperties;
import com.codeit.sb13.monew.article.service.ArticleService;
import com.codeit.sb13.monew.article.service.NewsSourceAdapter;
import com.codeit.sb13.monew.article.service.dto.ArticleRequest;
import com.codeit.sb13.monew.article.service.dto.CollectedArticle;
import com.codeit.sb13.monew.global.exception.article.ArticleDuplicateException;
import com.codeit.sb13.monew.global.service.AdvisoryLockService;
import com.codeit.sb13.monew.interest.service.InterestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("기사 수집 스케줄 작업 단위 테스트")
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ArticleCollectJobServiceTest {

    private static final String CRON = "0 0/10 * * * *";

    @Mock
    private NewsSourceAdapter naverAdapter;

    @Mock
    private NewsSourceAdapter rssAdapter;

    @Mock
    private ArticleService articleService;

    @Mock
    private InterestService interestService;

    @Mock
    private AdvisoryLockService advisoryLockService;

    @Test
    @DisplayName("스케줄이 비활성화되어 있으면 수집을 수행하지 않는다")
    void skipsWhenScheduleDisabled(CapturedOutput output) {
        ArticleCollectJobService service = service(false, naverAdapter);

        service.collectNewArticles();

        verifyNoInteractions(advisoryLockService, naverAdapter, articleService, interestService);
        assertThat(output).contains("기사 수집 스케줄러가 비활성화되어 작업을 건너뜁니다.");
    }

    @Test
    @DisplayName("락을 획득하지 못하면 경고 로그를 남기고 수집을 수행하지 않는다")
    void skipsWhenLockNotAcquired(CapturedOutput output) {
        ArticleCollectJobService service = service(true, naverAdapter);
        when(advisoryLockService.executeWithLock(anyString(), any(Runnable.class))).thenReturn(false);

        service.collectNewArticles();

        verifyNoInteractions(naverAdapter, articleService, interestService);
        assertThat(output).contains("이미 다른 스케줄러가 기사 수집을 실행 중입니다.");
    }

    @Test
    @DisplayName("락을 획득하면 모든 출처에서 기사를 수집해 저장하고, 새로 저장된 기사로 관심사 알림을 보낸다")
    void collectsFromAllSourcesAndNotifiesWhenLockAcquired() {
        ArticleCollectJobService service = service(true, naverAdapter, rssAdapter);
        runWithAcquiredLock();

        CollectedArticle naverArticle = collected(ArticleSource.NAVER, "네이버 기사", "https://naver.example/1");
        CollectedArticle rssArticle = collected(ArticleSource.CHOSUN, "조선 기사", "https://chosun.example/1");
        when(naverAdapter.fetch()).thenReturn(List.of(naverArticle));
        when(rssAdapter.fetch()).thenReturn(List.of(rssArticle));

        Article savedNaverArticle = article(naverArticle);
        Article savedRssArticle = article(rssArticle);
        when(articleService.create(requestOf(naverArticle))).thenReturn(savedNaverArticle);
        when(articleService.create(requestOf(rssArticle))).thenReturn(savedRssArticle);

        service.collectNewArticles();

        ArgumentCaptor<List<Article>> captor = ArgumentCaptor.forClass(List.class);
        verify(interestService).notifyForNewArticles(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(savedNaverArticle, savedRssArticle);
    }

    @Test
    @DisplayName("이미 저장된(링크 중복) 기사는 건너뛰고 나머지 기사는 그대로 저장한다")
    void duplicateArticle_isSkippedWithoutBlockingOthers() {
        ArticleCollectJobService service = service(true, naverAdapter);
        runWithAcquiredLock();

        CollectedArticle duplicate = collected(ArticleSource.NAVER, "이미 있는 기사", "https://naver.example/dup");
        CollectedArticle fresh = collected(ArticleSource.NAVER, "새 기사", "https://naver.example/new");
        when(naverAdapter.fetch()).thenReturn(List.of(duplicate, fresh));

        when(articleService.create(requestOf(duplicate))).thenThrow(new ArticleDuplicateException());
        Article savedFresh = article(fresh);
        when(articleService.create(requestOf(fresh))).thenReturn(savedFresh);

        service.collectNewArticles();

        ArgumentCaptor<List<Article>> captor = ArgumentCaptor.forClass(List.class);
        verify(interestService).notifyForNewArticles(captor.capture());
        assertThat(captor.getValue()).containsExactly(savedFresh);
    }

    @Test
    @DisplayName("새로 저장된 기사가 하나도 없으면 관심사 알림을 보내지 않는다")
    void noNewArticles_doesNotNotify() {
        ArticleCollectJobService service = service(true, naverAdapter);
        runWithAcquiredLock();

        CollectedArticle duplicate = collected(ArticleSource.NAVER, "이미 있는 기사", "https://naver.example/dup");
        when(naverAdapter.fetch()).thenReturn(List.of(duplicate));
        when(articleService.create(requestOf(duplicate))).thenThrow(new ArticleDuplicateException());

        service.collectNewArticles();

        verify(interestService, never()).notifyForNewArticles(any());
    }

    @Test
    @DisplayName("한 출처의 기사 수집이 실패해도 나머지 출처의 수집은 계속 진행한다")
    void oneSourceFailure_doesNotBlockOtherSources(CapturedOutput output) {
        ArticleCollectJobService service = service(true, naverAdapter, rssAdapter);
        runWithAcquiredLock();

        when(naverAdapter.fetch()).thenThrow(new RuntimeException("네이버 API 장애"));
        when(naverAdapter.source()).thenReturn(ArticleSource.NAVER);

        CollectedArticle rssArticle = collected(ArticleSource.CHOSUN, "조선 기사", "https://chosun.example/1");
        when(rssAdapter.fetch()).thenReturn(List.of(rssArticle));
        Article savedRssArticle = article(rssArticle);
        when(articleService.create(requestOf(rssArticle))).thenReturn(savedRssArticle);

        service.collectNewArticles();

        ArgumentCaptor<List<Article>> captor = ArgumentCaptor.forClass(List.class);
        verify(interestService).notifyForNewArticles(captor.capture());
        assertThat(captor.getValue()).containsExactly(savedRssArticle);
        assertThat(output).contains("NAVER 출처 기사 수집에 실패해 이번 배치에서 건너뜁니다.");
    }

    @Test
    @DisplayName("기사 저장 중 중복이 아닌 예외가 발생하면 그대로 전파한다")
    void propagatesNonDuplicateSaveFailure() {
        ArticleCollectJobService service = service(true, naverAdapter);
        runWithAcquiredLock();

        CollectedArticle collectedArticle = collected(ArticleSource.NAVER, "기사", "https://naver.example/1");
        when(naverAdapter.fetch()).thenReturn(List.of(collectedArticle));

        RuntimeException cause = new RuntimeException("db failure");
        doThrow(cause).when(articleService).create(requestOf(collectedArticle));

        assertThatThrownBy(service::collectNewArticles).isSameAs(cause);
    }

    private void runWithAcquiredLock() {
        when(advisoryLockService.executeWithLock(anyString(), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    invocation.getArgument(1, Runnable.class).run();
                    return true;
                });
    }

    private ArticleCollectJobService service(boolean enabled, NewsSourceAdapter... adapters) {
        return new ArticleCollectJobService(
                List.of(adapters),
                articleService,
                interestService,
                new ArticleCollectScheduleProperties(enabled, CRON),
                advisoryLockService
        );
    }

    private CollectedArticle collected(ArticleSource source, String title, String link) {
        return new CollectedArticle(source, title, "요약", link, LocalDateTime.of(2026, 8, 25, 9, 0));
    }

    private ArticleRequest requestOf(CollectedArticle collected) {
        return eq(new ArticleRequest(
                collected.title(),
                collected.summary(),
                collected.link(),
                collected.publishedAt(),
                collected.source()
        ));
    }

    private Article article(CollectedArticle collected) {
        return Article.create(
                collected.title(),
                collected.summary(),
                collected.link(),
                collected.publishedAt(),
                collected.source()
        );
    }
}
