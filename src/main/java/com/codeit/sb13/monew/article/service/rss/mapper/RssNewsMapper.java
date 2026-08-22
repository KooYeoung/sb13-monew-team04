package com.codeit.sb13.monew.article.service.rss.mapper;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.CollectedArticle;
import com.codeit.sb13.monew.global.exception.article.ArticleFetchParseException;
import com.codeit.sb13.monew.global.exception.article.ArticleFetchRequestInvalidException;
import com.rometools.modules.content.ContentModule;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Component
public class RssNewsMapper {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("[\\s\\u00A0\\u2000-\\u200D\\u202F\\u205F\\u2060\\u3000\\u3164\\uFEFF]+");

    public List<CollectedArticle> toCollectedArticles(ArticleSource source, String xmlBody) {
        if (source == null) {
            throw new ArticleFetchRequestInvalidException("source");
        }
        if (xmlBody == null) {
            throw new ArticleFetchParseException(source.name(), new IllegalArgumentException("xmlBody"));
        }

        SyndFeed feed = parseFeed(source, xmlBody);

        return feed.getEntries()
                .stream()
                .map(entry -> toArticle(source, entry))
                .flatMap(Optional::stream)
                .toList();
    }

    private SyndFeed parseFeed(ArticleSource source, String xmlBody) {
        try {
            SyndFeedInput input = new SyndFeedInput();
            input.setPreserveWireFeed(true);
            return input.build(new StringReader(xmlBody));
        } catch (FeedException e) {
            throw new ArticleFetchParseException(source.name(), e);
        }
    }

    private Optional<CollectedArticle> toArticle(ArticleSource source, SyndEntry entry) {
        String title = normalizeText(entry.getTitle());
        String link = normalizeText(entry.getLink());
        if (!StringUtils.hasText(link)) {
            log.warn("RSS 기사 링크를 확인할 수 없어 수집 후보에서 제외합니다. source={}, title={}",
                    source, title);
            return Optional.empty();
        }

        LocalDateTime publishedAt = parsePublishedAt(entry);
        if (publishedAt == null) {
            log.warn("RSS 기사 발행일을 확인할 수 없어 수집 후보에서 제외합니다. source={}, title={}, link={}",
                    source, title, link);
            return Optional.empty();
        }

        return Optional.of(new CollectedArticle(
                source,
                title,
                resolveSummary(entry),
                link,
                publishedAt
        ));
    }

    private LocalDateTime parsePublishedAt(SyndEntry entry) {
        Date publishedDate = entry.getPublishedDate();
        if (publishedDate == null) {
            return null;
        }

        return LocalDateTime.ofInstant(publishedDate.toInstant(), ZoneId.systemDefault());
    }

    private String resolveSummary(SyndEntry entry) {
        String description = cleanSummary(descriptionValue(entry));
        if (StringUtils.hasText(description)) {
            return description;
        }
        String content = cleanSummary(contentEncodedValue(entry));
        return StringUtils.hasText(content) ? content : null;
    }

    private String cleanSummary(String value) {
        String decoded = StringEscapeUtils.unescapeHtml4(stripCdata(value));
        String withoutHtml = HTML_TAG_PATTERN.matcher(decoded == null ? "" : decoded).replaceAll(" ");

        return normalizeText(StringEscapeUtils.unescapeHtml4(withoutHtml));
    }

    private String descriptionValue(SyndEntry entry) {
        SyndContent description = entry.getDescription();
        return description == null ? null : description.getValue();
    }

    private String contentEncodedValue(SyndEntry entry) {
        ContentModule module = (ContentModule) entry.getModule(ContentModule.URI);
        if (module == null || module.getEncodeds() == null) {
            return null;
        }

        return module.getEncodeds()
                .stream()
                .filter(this::hasTextAfterCleaning)
                .findFirst()
                .orElse(null);
    }

    private boolean hasTextAfterCleaning(String value) {
        return StringUtils.hasText(cleanSummary(value));
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String decoded = StringEscapeUtils.unescapeHtml4(stripCdata(value));
        String normalizedText = WHITESPACE_PATTERN.matcher(decoded).replaceAll(" ").trim();
        return normalizedText.isEmpty() ? null : normalizedText;
    }

    private String stripCdata(String value) {
        if (value == null) {
            return null;
        }

        return value.replace("<![CDATA[", "")
                .replace("]]>", "");
    }
}
