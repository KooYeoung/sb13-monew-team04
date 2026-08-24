package com.codeit.sb13.monew.article.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.domain.ArticleView;
import com.codeit.sb13.monew.article.repository.dto.ArticleSearchCondition;
import com.codeit.sb13.monew.article.repository.dto.ArticleSearchRow;
import com.codeit.sb13.monew.global.config.JpaAuditingConfig;
import com.codeit.sb13.monew.global.config.QueryDslConfig;
import com.codeit.sb13.monew.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * <p>{@code @DataJpaTest}는 일반 {@code @Configuration}을 스캔하지 않는다.
 * {@code JPAQueryFactory}를 쓰는 커스텀 구현에는 {@link QueryDslConfig}가,
 * 정렬 보조 기준인 {@code createdAt}이 채워지려면 {@link JpaAuditingConfig}가
 * 필요해 둘 다 명시적으로 {@code @Import}한다.</p>
 */
@DataJpaTest
@Import({QueryDslConfig.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
@DisplayName("ArticleRepositoryCustomImpl 통합 테스트")
class ArticleRepositoryCustomImplTest {

    private static final LocalDateTime D1 = LocalDateTime.of(2026, 8, 1, 9, 0);
    private static final LocalDateTime D2 = LocalDateTime.of(2026, 8, 5, 9, 0);
    private static final LocalDateTime D3 = LocalDateTime.of(2026, 8, 10, 9, 0);

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private TestEntityManager em;

    private User persistUser() {
        User user = User.builder()
                .email(UUID.randomUUID() + "@test.com")
                .nickname("테스터")
                .password("password")
                .build();
        em.persist(user);
        return user;
    }

    private Article persistArticle(String title, String summary,
                                   LocalDateTime date, ArticleSource source) {
        // link에 uk_articles_link UNIQUE 제약이 있어 매번 다른 값을 넣는다.
        Article article = Article.create(
                title, summary, "https://example.com/" + UUID.randomUUID(), date, source);
        em.persist(article);
        return article;
    }

    private void view(Article article, User user) {
        em.persist(ArticleView.create(article, user, LocalDateTime.now()));
    }

    /**
     * 1차 캐시가 아니라 실제 쿼리 결과를 검증하도록 flush/clear 후 조회한다.
     */
    private List<ArticleSearchRow> search(ArticleSearchCondition condition) {
        em.flush();
        em.clear();
        return articleRepository.search(condition);
    }

    private ArticleSearchCondition condition(String keyword, List<ArticleSource> sourceIn,
                                             LocalDateTime from, LocalDateTime to, UUID userId) {
        return new ArticleSearchCondition(keyword, sourceIn, from, to, userId);
    }

    private List<String> titlesOf(List<ArticleSearchRow> rows) {
        return rows.stream().map(row -> row.article().getTitle()).toList();
    }

    @Test
    @DisplayName("필터가 없으면 활성 기사 전체를 발행일 내림차순으로 반환한다")
    void searchWithoutFilter() {
        persistArticle("가장 오래된 기사", "요약", D1, ArticleSource.NAVER);
        persistArticle("중간 기사", "요약", D2, ArticleSource.CHOSUN);
        persistArticle("가장 최신 기사", "요약", D3, ArticleSource.HANKYUNG);

        List<ArticleSearchRow> rows = search(condition(null, null, null, null, null));

        assertThat(titlesOf(rows))
                .containsExactly("가장 최신 기사", "중간 기사", "가장 오래된 기사");
    }

    @Test
    @DisplayName("keyword로 제목을 검색한다")
    void searchByKeywordInTitle() {
        persistArticle("반도체 수출 증가", "본문 요약", D1, ArticleSource.NAVER);
        persistArticle("환율 급등", "본문 요약", D2, ArticleSource.NAVER);

        List<ArticleSearchRow> rows = search(condition("반도체", null, null, null, null));

        assertThat(titlesOf(rows)).containsExactly("반도체 수출 증가");
    }

    @Test
    @DisplayName("keyword로 요약을 검색한다")
    void searchByKeywordInSummary() {
        persistArticle("제목 A", "반도체 업황이 개선되고 있다", D1, ArticleSource.NAVER);
        persistArticle("제목 B", "환율이 급등했다", D2, ArticleSource.NAVER);

        List<ArticleSearchRow> rows = search(condition("반도체", null, null, null, null));

        assertThat(titlesOf(rows)).containsExactly("제목 A");
    }

    @Test
    @DisplayName("keyword 검색은 대소문자를 구분하지 않는다")
    void searchByKeywordIgnoreCase() {
        persistArticle("Samsung Electronics", "요약", D1, ArticleSource.NAVER);

        List<ArticleSearchRow> rows = search(condition("samsung", null, null, null, null));

        assertThat(titlesOf(rows)).containsExactly("Samsung Electronics");
    }

    @Test
    @DisplayName("sourceIn으로 복수 출처를 필터한다")
    void searchBySourceIn() {
        persistArticle("네이버 기사", "요약", D1, ArticleSource.NAVER);
        persistArticle("한경 기사", "요약", D2, ArticleSource.HANKYUNG);
        persistArticle("조선 기사", "요약", D3, ArticleSource.CHOSUN);

        List<ArticleSearchRow> rows = search(condition(
                null, List.of(ArticleSource.NAVER, ArticleSource.CHOSUN), null, null, null));

        assertThat(titlesOf(rows)).containsExactlyInAnyOrder("네이버 기사", "조선 기사");
    }

    @Test
    @DisplayName("발행일 범위 필터는 경계값을 포함한다")
    void searchByPublishDateRange() {
        persistArticle("8월 1일 기사", "요약", D1, ArticleSource.NAVER);
        persistArticle("8월 5일 기사", "요약", D2, ArticleSource.NAVER);
        persistArticle("8월 10일 기사", "요약", D3, ArticleSource.NAVER);

        assertThat(titlesOf(search(condition(null, null, D2, D2, null))))
                .containsExactly("8월 5일 기사");
        assertThat(titlesOf(search(condition(null, null, D2, null, null))))
                .containsExactly("8월 10일 기사", "8월 5일 기사");
        assertThat(titlesOf(search(condition(null, null, null, D2, null))))
                .containsExactly("8월 5일 기사", "8월 1일 기사");
    }

    @Test
    @DisplayName("백업 조회는 Article.date가 시작 시각 이상 종료 시각 미만인 기사만 반환한다")
    void findArticlesForBackupByPublishDateRange() {
        LocalDateTime fromInclusive = LocalDateTime.of(2026, 8, 23, 0, 0);
        LocalDateTime toExclusive = LocalDateTime.of(2026, 8, 24, 0, 0);
        persistArticle("범위 이전 기사", "요약", fromInclusive.minusSeconds(1), ArticleSource.NAVER);
        persistArticle("시작 경계 기사", "요약", fromInclusive, ArticleSource.NAVER);
        persistArticle("범위 내부 기사", "요약", LocalDateTime.of(2026, 8, 23, 12, 0), ArticleSource.NAVER);
        persistArticle("종료 경계 기사", "요약", toExclusive, ArticleSource.NAVER);

        em.flush();
        em.clear();

        List<Article> articles = articleRepository.findArticlesForBackup(fromInclusive, toExclusive);

        assertThat(articles)
                .extracting(Article::getTitle)
                .containsExactlyInAnyOrder("시작 경계 기사", "범위 내부 기사");
    }

    @Test
    @DisplayName("여러 필터를 동시에 적용해도 정상 동작한다")
    void searchByMultipleFilters() {
        persistArticle("반도체 수출 증가", "요약", D2, ArticleSource.NAVER);   // 전부 만족
        persistArticle("반도체 수출 감소", "요약", D2, ArticleSource.CHOSUN);  // 출처 불일치
        persistArticle("반도체 신규 공장", "요약", D3, ArticleSource.NAVER);   // 날짜 불일치
        persistArticle("환율 급등", "요약", D2, ArticleSource.NAVER);         // 키워드 불일치

        List<ArticleSearchRow> rows = search(condition(
                "반도체", List.of(ArticleSource.NAVER), D1, D2, null));

        assertThat(titlesOf(rows)).containsExactly("반도체 수출 증가");
    }

    @Test
    @DisplayName("논리 삭제된 기사는 목록에서 제외된다")
    void searchExcludesSoftDeleted() {
        persistArticle("살아있는 기사", "요약", D1, ArticleSource.NAVER);
        Article deleted = persistArticle("삭제된 기사", "요약", D2, ArticleSource.NAVER);
        deleted.softDelete();

        List<ArticleSearchRow> rows = search(condition(null, null, null, null, null));

        assertThat(titlesOf(rows)).containsExactly("살아있는 기사");
    }

    @Test
    @DisplayName("viewCount가 해당 기사의 조회 기록 수와 일치한다")
    void searchCalculatesViewCount() {
        Article viewed = persistArticle("조회된 기사", "요약", D2, ArticleSource.NAVER);
        Article notViewed = persistArticle("조회 안 된 기사", "요약", D1, ArticleSource.NAVER);
        view(viewed, persistUser());
        view(viewed, persistUser());

        List<ArticleSearchRow> rows = search(condition(null, null, null, null, null));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).article().getId()).isEqualTo(viewed.getId());
        assertThat(rows.get(0).viewCount()).isEqualTo(2L);
        assertThat(rows.get(1).article().getId()).isEqualTo(notViewed.getId());
        assertThat(rows.get(1).viewCount()).isZero();
    }

    @Test
    @DisplayName("viewedByMe가 요청자 기준으로 계산된다")
    void searchCalculatesViewedByMe() {
        User me = persistUser();
        User other = persistUser();
        Article readByMe = persistArticle("내가 본 기사", "요약", D2, ArticleSource.NAVER);
        Article readByOther = persistArticle("남이 본 기사", "요약", D1, ArticleSource.NAVER);
        view(readByMe, me);
        view(readByOther, other);

        List<ArticleSearchRow> rows = search(condition(null, null, null, null, me.getId()));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).viewedByMe()).isTrue();
        assertThat(rows.get(1).viewedByMe()).isFalse();
    }

    @Test
    @DisplayName("요청자가 없으면 viewedByMe는 모두 false다")
    void searchWithoutRequestUser() {
        Article article = persistArticle("기사", "요약", D1, ArticleSource.NAVER);
        view(article, persistUser());

        List<ArticleSearchRow> rows = search(condition(null, null, null, null, null));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).viewedByMe()).isFalse();
        assertThat(rows.get(0).viewCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("탈퇴한 사용자의 조회 이력은 viewCount에서 제외한다")
    void searchExcludesDeletedUserViews() {
        Article article = persistArticle("기사", "요약", D1, ArticleSource.NAVER);
        view(article, persistUser());
        User deleted = persistUser();
        view(article, deleted);
        deleted.softDelete();

        List<ArticleSearchRow> rows = search(condition(null, null, null, null, null));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).viewCount()).isEqualTo(1L);
    }
}
