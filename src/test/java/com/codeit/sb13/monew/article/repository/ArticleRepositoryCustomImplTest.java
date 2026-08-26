package com.codeit.sb13.monew.article.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.domain.ArticleView;
import com.codeit.sb13.monew.article.repository.dto.ArticleSearchCondition;
import com.codeit.sb13.monew.article.repository.dto.ArticleSearchPage;
import com.codeit.sb13.monew.article.repository.dto.ArticleSearchRow;
import com.codeit.sb13.monew.article.service.dto.ArticleOrderBy;
import com.codeit.sb13.monew.comment.domain.Comment;
import com.codeit.sb13.monew.global.config.JpaAuditingConfig;
import com.codeit.sb13.monew.global.exception.article.ArticleSearchConditionInvalidException;
import org.springframework.data.domain.Sort;
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
import java.util.ArrayList;
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

    private Comment comment(Article article, User user) {
        Comment comment = Comment.builder()
                .article(article)
                .user(user)
                .content("댓글")
                .build();
        em.persist(comment);
        return comment;
    }

    /**
     * 커서를 넘겨 다음 페이지를 조회하는 조건을 만든다.
     */
    private ArticleSearchCondition page(ArticleOrderBy orderBy, Sort.Direction direction,
                                        String cursor, LocalDateTime after, UUID idAfter,
                                        int limit) {
        return new ArticleSearchCondition(null, null, null, null,
                orderBy, direction, cursor, after, idAfter, limit, null);
    }

    /**
     * 한 페이지를 조회한 뒤 그 결과의 마지막 행에서 다음 커서를 뽑아 다시 조회한다.
     * 서비스가 하는 커서 계산과 같은 방식이라, 실제 페이지 넘김을 그대로 재현한다.
     */
    private ArticleSearchPage nextPage(ArticleSearchPage current, ArticleOrderBy orderBy,
                                       Sort.Direction direction, int limit) {
        List<ArticleSearchRow> rows = current.rows();
        ArticleSearchRow last = rows.get(rows.size() - 1);

        String cursor = switch (orderBy) {
            case COMMENT_COUNT -> String.valueOf(last.commentCount());
            case VIEW_COUNT -> String.valueOf(last.viewCount());
            case PUBLISH_DATE -> last.article().getDate().toString();
        };

        em.flush();
        em.clear();
        return articleRepository.search(page(orderBy, direction, cursor,
                last.article().getCreatedAt(), last.article().getId(), limit));
    }

    private ArticleSearchPage firstPage(ArticleOrderBy orderBy, Sort.Direction direction, int limit) {
        em.flush();
        em.clear();
        return articleRepository.search(page(orderBy, direction, null, null, null, limit));
    }

    /**
     * 1차 캐시가 아니라 실제 쿼리 결과를 검증하도록 flush/clear 후 조회한다.
     */
    private List<ArticleSearchRow> search(ArticleSearchCondition condition) {
        em.flush();
        em.clear();
        return articleRepository.search(condition).rows();
    }

    private ArticleSearchCondition condition(String keyword, List<ArticleSource> sourceIn,
                                             LocalDateTime from, LocalDateTime to, UUID userId) {
        // 이 헬퍼의 기본값: 발행일 내림차순, 커서 없음.
        return new ArticleSearchCondition(keyword, sourceIn, from, to,
                ArticleOrderBy.PUBLISH_DATE, Sort.Direction.DESC, null, null, null, 100, userId);
    }

    private ArticleSearchCondition sortedBy(ArticleOrderBy orderBy, Sort.Direction direction) {
        return new ArticleSearchCondition(null, null, null, null,
                orderBy, direction, null, null, null, 100, null);
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

    @Test
    @DisplayName("publishDate 오름차순 정렬")
    void searchOrderByPublishDateAsc() {
        persistArticle("가장 오래된 기사", "요약", D1, ArticleSource.NAVER);
        persistArticle("중간 기사", "요약", D2, ArticleSource.CHOSUN);
        persistArticle("가장 최신 기사", "요약", D3, ArticleSource.HANKYUNG);

        List<ArticleSearchRow> rows =
                search(sortedBy(ArticleOrderBy.PUBLISH_DATE, Sort.Direction.ASC));

        assertThat(titlesOf(rows))
                .containsExactly("가장 오래된 기사", "중간 기사", "가장 최신 기사");
    }

    @Test
    @DisplayName("publishDate 내림차순 정렬")
    void searchOrderByPublishDateDesc() {
        persistArticle("가장 오래된 기사", "요약", D1, ArticleSource.NAVER);
        persistArticle("중간 기사", "요약", D2, ArticleSource.CHOSUN);
        persistArticle("가장 최신 기사", "요약", D3, ArticleSource.HANKYUNG);

        List<ArticleSearchRow> rows =
                search(sortedBy(ArticleOrderBy.PUBLISH_DATE, Sort.Direction.DESC));

        assertThat(titlesOf(rows))
                .containsExactly("가장 최신 기사", "중간 기사", "가장 오래된 기사");
    }


    @Test
    @DisplayName("commentCount는 논리 삭제된 댓글과 탈퇴 사용자의 댓글을 제외한다")
    void commentCountExcludesDeletedCommentsAndWithdrawnUsers() {
        Article article = persistArticle("기사", "요약", D1, ArticleSource.NAVER);
        User active = persistUser();
        User withdrawn = persistUser();

        comment(article, active);              // 집계 대상
        comment(article, active);              // 집계 대상
        Comment deleted = comment(article, active);
        deleted.softDelete();                  // 논리 삭제 -> 제외
        comment(article, withdrawn);
        withdrawn.softDelete();                // 탈퇴 사용자 -> 제외

        List<ArticleSearchRow> rows = search(condition(null, null, null, null, null));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).commentCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("viewCount 내림차순 정렬")
    void searchOrderByViewCountDesc() {
        Article few = persistArticle("조회 적음", "요약", D1, ArticleSource.NAVER);
        Article many = persistArticle("조회 많음", "요약", D2, ArticleSource.CHOSUN);
        User u1 = persistUser();
        User u2 = persistUser();

        view(few, u1);
        view(many, u1);
        view(many, u2);

        List<ArticleSearchRow> rows =
                search(sortedBy(ArticleOrderBy.VIEW_COUNT, Sort.Direction.DESC));

        assertThat(titlesOf(rows)).containsExactly("조회 많음", "조회 적음");
    }

    @Test
    @DisplayName("commentCount 오름차순 정렬")
    void searchOrderByCommentCountAsc() {
        Article many = persistArticle("댓글 많음", "요약", D1, ArticleSource.NAVER);
        Article few = persistArticle("댓글 적음", "요약", D2, ArticleSource.CHOSUN);
        User user = persistUser();

        comment(many, user);
        comment(many, user);
        comment(few, user);

        List<ArticleSearchRow> rows =
                search(sortedBy(ArticleOrderBy.COMMENT_COUNT, Sort.Direction.ASC));

        assertThat(titlesOf(rows)).containsExactly("댓글 적음", "댓글 많음");
    }

    @Test
    @DisplayName("커서로 페이지를 끝까지 넘겨도 중복이나 누락이 없다")
    void cursorPagingCoversAllRowsWithoutDuplicates() {
        for (int i = 1; i <= 7; i++) {
            persistArticle("기사" + i, "요약", D1.plusDays(i), ArticleSource.NAVER);
        }

        List<String> collected = new ArrayList<>();
        ArticleSearchPage page = firstPage(ArticleOrderBy.PUBLISH_DATE, Sort.Direction.DESC, 3);
        collected.addAll(titlesOf(page.rows()));

        while (page.hasNext()) {
            page = nextPage(page, ArticleOrderBy.PUBLISH_DATE, Sort.Direction.DESC, 3);
            collected.addAll(titlesOf(page.rows()));
        }

        assertThat(collected).containsExactly(
                "기사7", "기사6", "기사5", "기사4", "기사3", "기사2", "기사1");
        assertThat(page.hasNext()).isFalse();
        assertThat(page.totalElements()).isEqualTo(7L);
    }

    @Test
    @DisplayName("정렬 값이 모두 동점이어도 커서 페이지네이션이 안정적으로 동작한다")
    void cursorPagingIsStableWhenPrimaryKeyTies() {
        // 발행일이 전부 같아 주 기준으로는 순서가 갈리지 않는다.
        // createdAt까지 같아지면 id 타이브레이커가 순서를 확정해야 한다.
        for (int i = 1; i <= 6; i++) {
            persistArticle("동점" + i, "요약", D1, ArticleSource.NAVER);
        }

        List<String> collected = new ArrayList<>();
        ArticleSearchPage page = firstPage(ArticleOrderBy.PUBLISH_DATE, Sort.Direction.DESC, 2);
        collected.addAll(titlesOf(page.rows()));

        while (page.hasNext()) {
            page = nextPage(page, ArticleOrderBy.PUBLISH_DATE, Sort.Direction.DESC, 2);
            collected.addAll(titlesOf(page.rows()));
        }

        assertThat(collected).hasSize(6);
        assertThat(collected).doesNotHaveDuplicates();
        assertThat(collected).containsExactlyInAnyOrder(
                "동점1", "동점2", "동점3", "동점4", "동점5", "동점6");
    }

    @Test
    @DisplayName("마지막 페이지에서는 hasNext가 false다")
    void lastPageHasNoNext() {
        persistArticle("기사1", "요약", D1, ArticleSource.NAVER);
        persistArticle("기사2", "요약", D2, ArticleSource.CHOSUN);

        ArticleSearchPage page = firstPage(ArticleOrderBy.PUBLISH_DATE, Sort.Direction.DESC, 5);

        assertThat(page.rows()).hasSize(2);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.totalElements()).isEqualTo(2L);
    }


    @Test
    @DisplayName("커서 세 값 중 일부만 오면 예외를 던진다")
    void partialCursorIsRejected() {
        persistArticle("기사", "요약", D1, ArticleSource.NAVER);

        // cursor만 있고 after, idAfter가 없다.
        assertThatThrownBy(() -> articleRepository.search(
                page(ArticleOrderBy.PUBLISH_DATE, Sort.Direction.DESC,
                        D1.toString(), null, null, 10)))
                .isInstanceOf(ArticleSearchConditionInvalidException.class);
    }

    @Test
    @DisplayName("viewCount 정렬에서도 커서로 페이지를 끝까지 넘길 수 있다")
    void cursorPagingWorksForViewCountOrdering() {
        User u1 = persistUser();
        User u2 = persistUser();
        User u3 = persistUser();

        Article three = persistArticle("조회3", "요약", D1, ArticleSource.NAVER);
        Article two = persistArticle("조회2", "요약", D2, ArticleSource.CHOSUN);
        Article one = persistArticle("조회1", "요약", D3, ArticleSource.HANKYUNG);
        Article zero = persistArticle("조회0", "요약", D1, ArticleSource.NAVER);

        view(three, u1);
        view(three, u2);
        view(three, u3);
        view(two, u1);
        view(two, u2);
        view(one, u1);

        List<String> collected = new ArrayList<>();
        ArticleSearchPage p = firstPage(ArticleOrderBy.VIEW_COUNT, Sort.Direction.DESC, 2);
        collected.addAll(titlesOf(p.rows()));

        while (p.hasNext()) {
            p = nextPage(p, ArticleOrderBy.VIEW_COUNT, Sort.Direction.DESC, 2);
            collected.addAll(titlesOf(p.rows()));
        }

        assertThat(collected).containsExactly("조회3", "조회2", "조회1", "조회0");
        assertThat(zero).isNotNull();
    }

    @Test
    @DisplayName("commentCount 정렬에서도 커서로 페이지를 끝까지 넘길 수 있다")
    void cursorPagingWorksForCommentCountOrdering() {
        User user = persistUser();

        Article three = persistArticle("댓글3", "요약", D1, ArticleSource.NAVER);
        Article two = persistArticle("댓글2", "요약", D2, ArticleSource.CHOSUN);
        Article one = persistArticle("댓글1", "요약", D3, ArticleSource.HANKYUNG);
        persistArticle("댓글0", "요약", D1, ArticleSource.NAVER);

        comment(three, user);
        comment(three, user);
        comment(three, user);
        comment(two, user);
        comment(two, user);
        comment(one, user);

        List<String> collected = new ArrayList<>();
        ArticleSearchPage p = firstPage(ArticleOrderBy.COMMENT_COUNT, Sort.Direction.ASC, 2);
        collected.addAll(titlesOf(p.rows()));

        while (p.hasNext()) {
            p = nextPage(p, ArticleOrderBy.COMMENT_COUNT, Sort.Direction.ASC, 2);
            collected.addAll(titlesOf(p.rows()));
        }

        assertThat(collected).containsExactly("댓글0", "댓글1", "댓글2", "댓글3");
    }
}
