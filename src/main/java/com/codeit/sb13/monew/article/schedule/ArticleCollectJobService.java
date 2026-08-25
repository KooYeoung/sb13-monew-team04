package com.codeit.sb13.monew.article.schedule;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.schedule.config.ArticleCollectScheduleProperties;
import com.codeit.sb13.monew.article.service.ArticleService;
import com.codeit.sb13.monew.article.service.NewsSourceAdapter;
import com.codeit.sb13.monew.article.service.dto.ArticleRequest;
import com.codeit.sb13.monew.article.service.dto.CollectedArticle;
import com.codeit.sb13.monew.global.exception.article.ArticleDuplicateException;
import com.codeit.sb13.monew.global.service.AdvisoryLockService;
import com.codeit.sb13.monew.interest.service.InterestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 등록된 모든 {@link NewsSourceAdapter}로부터 신규 기사를 수집해 저장하고,
 * 새로 저장된 기사를 관심사 키워드와 매칭해 구독자에게 알림을 보낸다.
 *
 * <p>{@link BackupJobService}와 마찬가지로 {@link AdvisoryLockService}로 감싸,
 * 여러 인스턴스에서 스케줄러가 동시에 실행되거나 이전 실행이 아직 끝나지 않은
 * 상태에서 다음 주기가 도래해도 한쪽만 실제로 수집을 수행하게 한다. 다만 백업
 * 작업과 달리 특정 날짜에 종속된 작업이 아니므로, 락 키는 날짜별로 바뀌지 않고
 * 고정된 값 하나를 계속 재사용한다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ArticleCollectJobService {

    private static final String LOCK_KEY = "article-collect";

    private final List<NewsSourceAdapter> newsSourceAdapters;
    private final ArticleService articleService;
    private final InterestService interestService;
    private final ArticleCollectScheduleProperties props;
    private final AdvisoryLockService advisoryLockService;

    @Scheduled(cron = "${monew.collect.schedule.cron}")
    public void collectNewArticles() {
        if (!props.enabled()) {
            log.info("기사 수집 스케줄러가 비활성화되어 작업을 건너뜁니다.");
            return;
        }

        boolean executed = advisoryLockService.executeWithLock(LOCK_KEY, this::collectAndNotify);
        if (!executed) {
            log.warn("이미 다른 스케줄러가 기사 수집을 실행 중입니다.");
            return;
        }

        log.info("기사 수집 스케줄러 작업을 완료했습니다.");
    }

    private void collectAndNotify() {
        List<Article> savedArticles = collectAndSaveArticles();
        if (savedArticles.isEmpty()) {
            return;
        }

        interestService.notifyForNewArticles(savedArticles);
    }

    /**
     * 모든 출처에서 기사를 수집해 저장한다.
     *
     * <p>출처 하나를 수집하다 실패해도(외부 API/RSS 장애 등) 나머지 출처 수집까지
     * 막지는 않는다. 각 출처는 서로 독립적인 외부 호출이라, 한 출처가 일시적으로
     * 응답하지 않는다고 해서 나머지 출처의 신규 기사까지 놓칠 이유는 없다.</p>
     */
    private List<Article> collectAndSaveArticles() {
        List<Article> savedArticles = new ArrayList<>();
        for (NewsSourceAdapter adapter : newsSourceAdapters) {
            savedArticles.addAll(collectAndSave(adapter));
        }
        return savedArticles;
    }

    private List<Article> collectAndSave(NewsSourceAdapter adapter) {
        List<CollectedArticle> collectedArticles;
        try {
            collectedArticles = adapter.fetch();
        } catch (RuntimeException e) {
            log.warn("{} 출처 기사 수집에 실패해 이번 배치에서 건너뜁니다.", adapter.source(), e);
            return List.of();
        }

        List<Article> saved = new ArrayList<>();
        for (CollectedArticle collected : collectedArticles) {
            saveIfNew(collected).ifPresent(saved::add);
        }
        return saved;
    }

    /**
     * 이미 저장된(링크 중복) 기사는 예외 없이 건너뛴다.
     *
     * <p>{@link ArticleService#create}는 저장 직전 명시적 중복 체크뿐 아니라, 두 요청이
     * 거의 동시에 같은 링크를 저장하려는 경합 상황까지 {@link ArticleDuplicateException}
     * 하나로 통일해 던지므로, 여기서는 그 예외 하나만 잡으면 된다.</p>
     */
    private Optional<Article> saveIfNew(CollectedArticle collected) {
        try {
            return Optional.of(articleService.create(toRequest(collected)));
        } catch (ArticleDuplicateException e) {
            return Optional.empty();
        }
    }

    private ArticleRequest toRequest(CollectedArticle collected) {
        return new ArticleRequest(
                collected.title(),
                collected.summary(),
                collected.link(),
                collected.publishedAt(),
                collected.source()
        );
    }
}
