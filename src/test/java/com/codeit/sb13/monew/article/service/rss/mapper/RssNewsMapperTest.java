package com.codeit.sb13.monew.article.service.rss.mapper;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.CollectedArticle;
import com.codeit.sb13.monew.global.exception.article.ArticleFetchParseException;
import com.codeit.sb13.monew.global.exception.article.ArticleFetchRequestInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
@DisplayName("RssNewsMapper 단위 테스트")
class RssNewsMapperTest {

    private final RssNewsMapper mapper = new RssNewsMapper();

    @Test
    @DisplayName("빈 RSS feed는 빈 목록을 반환한다")
    void returnsEmptyListWhenFeedHasNoItems() {
        List<CollectedArticle> articles = mapper.toCollectedArticles(ArticleSource.HANKYUNG, rss(""));

        assertThat(articles).isEmpty();
    }

    @Test
    @DisplayName("RSS item 필드를 수집 기사 후보로 변환한다")
    void mapsRssItemToCollectedArticle() {
        String xml = rss("""
                <item>
                    <title><![CDATA[Title &amp; News]]></title>
                    <link><![CDATA[https://example.com/articles/1?x=1&amp;y=2]]></link>
                    <description><![CDATA[Summary &amp; description]]></description>
                    <pubDate>Fri, 21 Aug 2026 10:28:09 +0900</pubDate>
                </item>
                """);

        List<CollectedArticle> articles = mapper.toCollectedArticles(ArticleSource.CHOSUN, xml);

        assertThat(articles).singleElement().satisfies(article -> {
            assertThat(article.source()).isEqualTo(ArticleSource.CHOSUN);
            assertThat(article.title()).isEqualTo("Title & News");
            assertThat(article.summary()).isEqualTo("Summary & description");
            assertThat(article.link()).isEqualTo("https://example.com/articles/1?x=1&y=2");
            assertThat(article.publishedAt()).isEqualTo(expectedPublishedAt());
        });
    }

    @Test
    @DisplayName("description 정리 결과가 비어 있으면 content:encoded를 사용한다")
    void usesContentEncodedWhenDescriptionIsBlankAfterCleaning() {
        String xml = rss("""
                <item>
                    <title>Title</title>
                    <link>https://example.com/articles/1</link>
                    <description><![CDATA[<p>ㅤ&nbsp;</p>]]></description>
                    <content:encoded><![CDATA[<div>Content&nbsp;<b>summary</b></div>]]></content:encoded>
                    <pubDate>Fri, 21 Aug 2026 10:28:09 +0900</pubDate>
                </item>
                """);

        List<CollectedArticle> articles = mapper.toCollectedArticles(ArticleSource.YEONHAP, xml);

        assertThat(articles).singleElement()
                .satisfies(article -> assertThat(article.summary()).isEqualTo("Content summary"));
    }

    @Test
    @DisplayName("description과 content:encoded가 모두 비어 있으면 summary를 빈 문자열로 반환한다")
    void returnsEmptySummaryWhenSummaryCandidatesAreBlank() {
        String xml = rss("""
                <item>
                    <title>Title</title>
                    <link>https://example.com/articles/1</link>
                    <description><![CDATA[<br />]]></description>
                    <content:encoded><![CDATA[<p>ㅤ</p>]]></content:encoded>
                    <pubDate>Fri, 21 Aug 2026 10:28:09 +0900</pubDate>
                </item>
                """);

        List<CollectedArticle> articles = mapper.toCollectedArticles(ArticleSource.HANKYUNG, xml);

        assertThat(articles).singleElement()
                .satisfies(article -> assertThat(article.summary()).isEmpty());
    }

    @Test
    @DisplayName("description과 content:encoded가 없는 item은 summary를 빈 문자열로 반환한다")
    void returnsEmptySummaryWhenSummaryCandidatesAreMissing() {
        String xml = rss("""
                <item>
                    <title>Title</title>
                    <link>https://example.com/articles/1</link>
                    <pubDate>Fri, 21 Aug 2026 10:28:09 +0900</pubDate>
                </item>
                """);

        List<CollectedArticle> articles = mapper.toCollectedArticles(ArticleSource.HANKYUNG, xml);

        assertThat(articles).singleElement()
                .satisfies(article -> assertThat(article.summary()).isEmpty());
    }

    @Test
    @DisplayName("link가 없는 item은 제외하고 로그를 남긴다")
    void skipsItemWithoutLink(CapturedOutput output) {
        String xml = rss("""
                <item>
                    <title>Title</title>
                    <description>Summary</description>
                    <pubDate>Fri, 21 Aug 2026 10:28:09 +0900</pubDate>
                </item>
                <item>
                    <title>Linked title</title>
                    <link>https://example.com/articles/2</link>
                    <description>Linked summary</description>
                    <pubDate>Fri, 21 Aug 2026 10:28:09 +0900</pubDate>
                </item>
                """);

        List<CollectedArticle> articles = mapper.toCollectedArticles(ArticleSource.CHOSUN, xml);

        assertThat(articles)
                .singleElement()
                .satisfies(article -> assertThat(article.link()).isEqualTo("https://example.com/articles/2"));
        assertThat(output).contains(
                "RSS 기사 링크를 확인할 수 없어 수집 후보에서 제외합니다.",
                "source=CHOSUN",
                "title=Title"
        );
    }

    @Test
    @DisplayName("pubDate가 없으면 item을 제외하고 로그를 남긴다")
    void skipsItemAndLogsWhenPubDateIsMissing(CapturedOutput output) {
        String xml = rss("""
                <item>
                    <title>Title</title>
                    <link>https://example.com/articles/1</link>
                    <description>Summary</description>
                </item>
                """);

        List<CollectedArticle> articles = mapper.toCollectedArticles(ArticleSource.YEONHAP, xml);

        assertThat(articles).isEmpty();
        assertThat(output).contains(
                "RSS 기사 발행일을 확인할 수 없어 수집 후보에서 제외합니다.",
                "source=YEONHAP",
                "title=Title",
                "link=https://example.com/articles/1"
        );
    }

    @Test
    @DisplayName("pubDate 파싱에 실패하면 item을 제외하고 로그를 남긴다")
    void skipsItemAndLogsWhenPubDateIsInvalid(CapturedOutput output) {
        String xml = rss("""
                <item>
                    <title>Title</title>
                    <link>https://example.com/articles/1</link>
                    <description>Summary</description>
                    <pubDate>invalid-date</pubDate>
                </item>
                """);

        List<CollectedArticle> articles = mapper.toCollectedArticles(ArticleSource.YEONHAP, xml);

        assertThat(articles).isEmpty();
        assertThat(output).contains(
                "RSS 기사 발행일을 확인할 수 없어 수집 후보에서 제외합니다.",
                "source=YEONHAP",
                "title=Title",
                "link=https://example.com/articles/1"
        );
    }

    @Test
    @DisplayName("XML 형식이 잘못되면 파싱 예외가 발생한다")
    void throwsParseExceptionWhenXmlIsMalformed() {
        assertThatThrownBy(() -> mapper.toCollectedArticles(ArticleSource.CHOSUN, "<rss><channel>"))
                .isInstanceOf(ArticleFetchParseException.class);
    }

    @Test
    @DisplayName("source가 null이면 요청 invalid 예외가 발생한다")
    void throwsRequestInvalidExceptionWhenSourceIsNull() {
        assertThatThrownBy(() -> mapper.toCollectedArticles(null, rss("")))
                .isInstanceOf(ArticleFetchRequestInvalidException.class);
    }

    @Test
    @DisplayName("XML body가 null이면 파싱 예외가 발생한다")
    void throwsParseExceptionWhenXmlBodyIsNull() {
        assertThatThrownBy(() -> mapper.toCollectedArticles(ArticleSource.CHOSUN, null))
                .isInstanceOf(ArticleFetchParseException.class);
    }

    private String rss(String items) {
        return """
                <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
                  <channel>
                    <title>Test feed</title>
                    <link>https://example.com</link>
                    <description>Test description</description>
                    %s
                  </channel>
                </rss>
                """.formatted(items);
    }

    private LocalDateTime expectedPublishedAt() {
        return ZonedDateTime.parse("Fri, 21 Aug 2026 10:28:09 +0900",
                        java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}
