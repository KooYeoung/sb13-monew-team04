package com.codeit.sb13.monew.article.service;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.repository.ArticleRepository;
import com.codeit.sb13.monew.article.repository.ArticleViewRepository;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기사 뷰 등록의 동시성 복구를 검증한다. (MID4-164)
 *
 * <p>클래스에 {@code @Transactional}을 붙이지 않는다. 테스트 트랜잭션이 열려 있으면 준비 데이터가
 * 커밋되지 않아 다른 스레드에서 보이지 않고, 각 스레드의 트랜잭션 경계도 성립하지 않는다.
 * 대신 {@code @AfterEach}에서 직접 정리한다.
 */
@SpringBootTest
@DisplayName("기사 뷰 등록 동시성 통합 테스트")
class ArticleViewConcurrencyTest {

    @Autowired
    private ArticleViewService articleViewService;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ArticleViewRepository articleViewRepository;

    private UUID articleId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        Article article = articleRepository.save(Article.create(
                "동시성 테스트 기사",
                "요약",
                "https://example.com/concurrency-" + UUID.randomUUID(),
                LocalDateTime.now(),
                ArticleSource.NAVER));

        User user = userRepository.save(User.builder()
                .email("concurrency-" + UUID.randomUUID() + "@example.com")
                .nickname("tester")
                .password("encoded-password")
                .build());

        articleId = article.getId();
        userId = user.getId();
    }

    @AfterEach
    void tearDown() {
        articleViewRepository.deleteAll();
    }

    @Test
    @DisplayName("같은 사용자의 동시 뷰 등록 요청이 모두 성공하고 조회 기록은 1건만 남는다")
    void concurrentRecordViewKeepsSingleRow() throws InterruptedException {
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();                       // 전원이 준비될 때까지 대기
                    articleViewService.recordView(articleId, userId);
                    successCount.incrementAndGet();
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();                               // 동시에 출발
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // then
        assertThat(failures).isEmpty();
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(articleViewRepository.countActiveByArticleId(articleId))
                .isEqualTo(1L);
    }
}
