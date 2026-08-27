package com.codeit.sb13.monew.article.s3.service;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.s3.service.dto.ArticleBackupFile;
import com.codeit.sb13.monew.article.s3.service.dto.ArticleBackupItem;
import com.codeit.sb13.monew.global.exception.article.ArticleBackupFileInvalidException;
import com.codeit.sb13.monew.global.exception.article.ArticleBackupFileJsonException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ArticleBackupFileJsonConverter 단위 테스트")
class ArticleBackupFileJsonConverterTest {

    private final ArticleBackupFileJsonConverter converter = new ArticleBackupFileJsonConverter(new ObjectMapper());

    @Test
    @DisplayName("UTF-8 JSON 직렬화와 역직렬화가 성공한다")
    void serializesAndDeserializesUtf8Json() {
        // given
        ArticleBackupFile backupFile = backupFile();

        // when
        String json = converter.serialize(backupFile);
        String utf8Json = new String(json.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        ArticleBackupFile result = converter.deserialize(utf8Json);

        // then
        assertThat(result).isEqualTo(backupFile);
        assertThat(result.articles().get(0).title()).isEqualTo("기사 제목");
        assertThat(result.articles().get(0).summary()).isEqualTo("기사 요약");
    }

    @Test
    @DisplayName("source를 enum 이름 문자열로 직렬화한다")
    void serializesSourceAsEnumNameString() {
        // when
        String json = converter.serialize(backupFile());

        // then
        assertThat(json).contains("\"source\":\"NAVER\"");
    }

    @Test
    @DisplayName("deletedAt이 null이어도 JSON 필드로 유지된다")
    void keepsDeletedAtFieldWhenDeletedAtIsNull() {
        // when
        String json = converter.serialize(backupFile());

        // then
        assertThat(json).contains("\"deletedAt\":null");
    }

    @Test
    @DisplayName("summary가 빈 문자열이어도 JSON 직렬화와 역직렬화가 성공한다")
    void serializesAndDeserializesEmptySummary() {
        // given
        ArticleBackupFile backupFile = backupFileWithSummary("");

        // when
        String json = converter.serialize(backupFile);
        ArticleBackupFile result = converter.deserialize(json);

        // then
        assertThat(json).contains("\"summary\":\"\"");
        assertThat(result.articles().get(0).summary()).isEmpty();
    }

    @Test
    @DisplayName("백업 파일이 없으면 백업 파일 검증 예외가 발생한다")
    void throwsInvalidExceptionWhenBackupFileIsNull() {
        assertThatThrownBy(() -> converter.serialize(null))
                .isInstanceOf(ArticleBackupFileInvalidException.class);
    }

    @Test
    @DisplayName("JSON 직렬화 실패 시 백업 파일 JSON 예외가 발생한다")
    void throwsJsonExceptionWhenSerializationFails() throws Exception {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ArticleBackupFileJsonConverter failingConverter = new ArticleBackupFileJsonConverter(objectMapper);
        ArticleBackupFile backupFile = backupFile();
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(DatabindException.from((SerializationContext) null, "직렬화 실패"));

        assertThatThrownBy(() -> failingConverter.serialize(backupFile))
                .isInstanceOf(ArticleBackupFileJsonException.class)
                .hasCauseInstanceOf(DatabindException.class);
    }

    @Test
    @DisplayName("JSON 문자열이 없으면 백업 파일 검증 예외가 발생한다")
    void throwsInvalidExceptionWhenJsonIsBlank() {
        assertThatThrownBy(() -> converter.deserialize(" "))
                .isInstanceOf(ArticleBackupFileInvalidException.class);
    }

    @Test
    @DisplayName("잘못된 JSON이면 백업 파일 JSON 예외가 발생한다")
    void throwsJsonExceptionWhenJsonIsMalformed() {
        assertThatThrownBy(() -> converter.deserialize("{"))
                .isInstanceOf(ArticleBackupFileJsonException.class);
    }

    @Test
    @DisplayName("필수 필드 누락 JSON이면 백업 파일 JSON 예외가 발생한다")
    void throwsJsonExceptionWhenRequiredFieldIsMissing() {
        // given
        String json = """
                {
                  "schemaVersion": 1,
                  "backupDate": "2026-08-23",
                  "generatedAt": "2026-08-24T00:10:00",
                  "articleCount": 1,
                  "articles": [
                    {
                      "originalArticleId": "00000000-0000-4000-8000-000000000001",
                      "source": "NAVER",
                      "link": "https://example.com/news/1",
                      "title": "기사 제목",
                      "summary": "기사 요약",
                      "deletedAt": null
                    }
                  ]
                }
                """;

        // when & then
        assertThatThrownBy(() -> converter.deserialize(json))
                .isInstanceOf(ArticleBackupFileJsonException.class);
    }

    private ArticleBackupFile backupFile() {
        return backupFileWithSummary("기사 요약");
    }

    private ArticleBackupFile backupFileWithSummary(String summary) {
        ArticleBackupItem item = new ArticleBackupItem(
                UUID.fromString("00000000-0000-4000-8000-000000000001"),
                ArticleSource.NAVER,
                "https://example.com/news/1",
                "기사 제목",
                summary,
                LocalDateTime.of(2026, 8, 23, 10, 15),
                null
        );

        return ArticleBackupFile.of(
                LocalDate.of(2026, 8, 23),
                LocalDateTime.of(2026, 8, 24, 0, 10),
                List.of(item)
        );
    }
}
