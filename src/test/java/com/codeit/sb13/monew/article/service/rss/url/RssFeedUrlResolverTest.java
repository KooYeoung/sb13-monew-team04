package com.codeit.sb13.monew.article.service.rss.url;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.rss.category.ChosunRssCategory;
import com.codeit.sb13.monew.article.service.rss.category.HankyungRssCategory;
import com.codeit.sb13.monew.article.service.rss.category.YonhapRssCategory;
import com.codeit.sb13.monew.global.exception.article.ArticleFetchRequestInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RSS feed URL resolver 단위 테스트")
class RssFeedUrlResolverTest {

    private final RssFeedUrlResolver resolver = new RssFeedUrlResolver();

    @Test
    @DisplayName("한국경제 카테고리 feed URL을 생성한다")
    void resolvesHankyungFeedUrl() {
        String url = resolver.resolve(
                ArticleSource.HANKYUNG,
                "https://www.hankyung.com/feed",
                HankyungRssCategory.ECONOMY
        );

        assertThat(url).isEqualTo("https://www.hankyung.com/feed/economy");
    }

    @Test
    @DisplayName("조선일보 전체기사 feed URL을 생성한다")
    void resolvesChosunAllFeedUrl() {
        String url = resolver.resolve(
                ArticleSource.CHOSUN,
                "https://www.chosun.com/arc/outboundfeeds/rss",
                ChosunRssCategory.ALL
        );

        assertThat(url).isEqualTo("https://www.chosun.com/arc/outboundfeeds/rss/?outputType=xml");
    }

    @Test
    @DisplayName("조선일보 카테고리 feed URL을 생성한다")
    void resolvesChosunCategoryFeedUrl() {
        String url = resolver.resolve(
                ArticleSource.CHOSUN,
                "https://www.chosun.com/arc/outboundfeeds/rss",
                ChosunRssCategory.ECONOMY
        );

        assertThat(url).isEqualTo("https://www.chosun.com/arc/outboundfeeds/rss/category/economy/?outputType=xml");
    }

    @Test
    @DisplayName("연합뉴스TV 최신 feed URL을 생성한다")
    void resolvesYonhapLatestFeedUrl() {
        String url = resolver.resolve(
                ArticleSource.YEONHAP,
                "https://www.yonhapnewstv.co.kr",
                YonhapRssCategory.LATEST
        );

        assertThat(url).isEqualTo("https://www.yonhapnewstv.co.kr/browse/feed/");
    }

    @Test
    @DisplayName("연합뉴스TV 카테고리 feed URL을 생성한다")
    void resolvesYonhapCategoryFeedUrl() {
        String url = resolver.resolve(
                ArticleSource.YEONHAP,
                "https://www.yonhapnewstv.co.kr",
                YonhapRssCategory.POLITICS
        );

        assertThat(url).isEqualTo("https://www.yonhapnewstv.co.kr/category/news/politics/feed/");
    }

    @Test
    @DisplayName("base URL 앞뒤 공백을 제거하고 feed URL을 생성한다")
    void stripsBaseUrlWhitespace() {
        String url = resolver.resolve(
                ArticleSource.HANKYUNG,
                " https://www.hankyung.com/feed ",
                HankyungRssCategory.ALL_NEWS
        );

        assertThat(url).isEqualTo("https://www.hankyung.com/feed/all-news");
    }

    @Test
    @DisplayName("RSS 출처가 아니면 요청 invalid 예외가 발생한다")
    void throwsInvalidRequestWhenSourceIsNotRssSource() {
        assertThatThrownBy(() -> resolver.resolve(
                ArticleSource.NAVER,
                "https://openapi.naver.com",
                HankyungRssCategory.ALL_NEWS
        )).isInstanceOf(ArticleFetchRequestInvalidException.class);
    }

    @Test
    @DisplayName("source가 null이면 요청 invalid 예외가 발생한다")
    void throwsInvalidRequestWhenSourceIsNull() {
        assertThatThrownBy(() -> resolver.resolve(
                null,
                "https://www.hankyung.com/feed",
                HankyungRssCategory.ALL_NEWS
        )).isInstanceOf(ArticleFetchRequestInvalidException.class);
    }

    @Test
    @DisplayName("base URL이 비어 있으면 요청 invalid 예외가 발생한다")
    void throwsInvalidRequestWhenBaseUrlIsBlank() {
        assertThatThrownBy(() -> resolver.resolve(
                ArticleSource.HANKYUNG,
                " ",
                HankyungRssCategory.ALL_NEWS
        )).isInstanceOf(ArticleFetchRequestInvalidException.class);
    }

    @Test
    @DisplayName("category가 null이면 요청 invalid 예외가 발생한다")
    void throwsInvalidRequestWhenCategoryIsNull() {
        assertThatThrownBy(() -> resolver.resolve(
                ArticleSource.HANKYUNG,
                "https://www.hankyung.com/feed",
                null
        )).isInstanceOf(ArticleFetchRequestInvalidException.class);
    }

    @Test
    @DisplayName("source와 category 출처가 다르면 요청 invalid 예외가 발생한다")
    void throwsInvalidRequestWhenSourceAndCategoryDoNotMatch() {
        assertThatThrownBy(() -> resolver.resolve(
                ArticleSource.HANKYUNG,
                "https://www.hankyung.com/feed",
                ChosunRssCategory.ALL
        )).isInstanceOf(ArticleFetchRequestInvalidException.class);
    }
}
