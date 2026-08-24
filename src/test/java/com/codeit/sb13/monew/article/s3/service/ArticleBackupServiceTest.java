package com.codeit.sb13.monew.article.s3.service;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.s3.service.dto.ArticleBackupFile;
import com.codeit.sb13.monew.article.s3.service.dto.ArticleBackupItem;
import com.codeit.sb13.monew.article.s3.service.dto.StorageCommand;
import com.codeit.sb13.monew.article.s3.service.dto.StorageSaveResult;
import com.codeit.sb13.monew.article.service.ArticleService;
import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import com.codeit.sb13.monew.global.exception.article.ArticleBackupDateInvalidException;
import com.codeit.sb13.monew.global.exception.article.ArticleBackupFailedException;
import com.codeit.sb13.monew.global.exception.article.ArticleBackupFileJsonException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ArticleBackupService 단위 테스트")
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ArticleBackupServiceTest {

    private static final LocalDate BACKUP_DATE = LocalDate.of(2026, 8, 23);
    private static final String CONTENT = "{\"schemaVersion\":1}";

    @Mock
    private ArticleService articleService;

    @Mock
    private ArticleBackupFileJsonConverter converter;

    @Mock
    private Storage storage;

    private ArticleBackupService service;

    @BeforeEach
    void setUp() {
        service = new ArticleBackupService(articleService, converter, storage);
    }

    @Test
    @DisplayName("지정한 날짜의 기사 백업 파일을 저장한다")
    void backsUpArticlesByDate() {
        ArticleBackupItem item = backupItem();
        when(articleService.findArticleBackupItemsByDateRange(BACKUP_DATE, BACKUP_DATE.plusDays(1)))
                .thenReturn(List.of(item));
        when(converter.serialize(any(ArticleBackupFile.class))).thenReturn(CONTENT);
        when(storage.saveIfAbsent(any(StorageCommand.class))).thenReturn(StorageSaveResult.SAVED);

        StorageSaveResult result = service.backupArticlesByDate(BACKUP_DATE);

        assertThat(result).isEqualTo(StorageSaveResult.SAVED);
        verify(articleService).findArticleBackupItemsByDateRange(BACKUP_DATE, BACKUP_DATE.plusDays(1));

        ArgumentCaptor<ArticleBackupFile> fileCaptor = ArgumentCaptor.forClass(ArticleBackupFile.class);
        verify(converter).serialize(fileCaptor.capture());
        ArticleBackupFile backupFile = fileCaptor.getValue();
        assertThat(backupFile.backupDate()).isEqualTo(BACKUP_DATE);
        assertThat(backupFile.articleCount()).isEqualTo(1L);
        assertThat(backupFile.articles()).containsExactly(item);

        ArgumentCaptor<StorageCommand> commandCaptor = ArgumentCaptor.forClass(StorageCommand.class);
        verify(storage).saveIfAbsent(commandCaptor.capture());
        assertThat(commandCaptor.getValue().backupDate()).isEqualTo(BACKUP_DATE);
        assertThat(commandCaptor.getValue().content()).isEqualTo(CONTENT);
    }

    @Test
    @DisplayName("동일 백업 파일이 이미 있으면 skip 결과를 반환한다")
    void skipsWhenBackupFileAlreadyExists(CapturedOutput output) {
        when(articleService.findArticleBackupItemsByDateRange(BACKUP_DATE, BACKUP_DATE.plusDays(1)))
                .thenReturn(List.of());
        when(converter.serialize(any(ArticleBackupFile.class))).thenReturn(CONTENT);
        when(storage.saveIfAbsent(any(StorageCommand.class))).thenReturn(StorageSaveResult.ALREADY_EXISTS);

        StorageSaveResult result = service.backupArticlesByDate(BACKUP_DATE);

        assertThat(result).isEqualTo(StorageSaveResult.ALREADY_EXISTS);
        assertThat(output).contains(
                "기사 백업 파일이 이미 존재하여 저장을 건너뜁니다.",
                "backupDate=2026-08-23",
                "result=ALREADY_EXISTS"
        );
    }

    @Test
    @DisplayName("저장 충돌 결과는 skip 로그와 분리해 경고 로그를 남긴다")
    void logsConflictResultSeparately(CapturedOutput output) {
        when(articleService.findArticleBackupItemsByDateRange(BACKUP_DATE, BACKUP_DATE.plusDays(1)))
                .thenReturn(List.of());
        when(converter.serialize(any(ArticleBackupFile.class))).thenReturn(CONTENT);
        when(storage.saveIfAbsent(any(StorageCommand.class))).thenReturn(StorageSaveResult.CONFLICT);

        StorageSaveResult result = service.backupArticlesByDate(BACKUP_DATE);

        assertThat(result).isEqualTo(StorageSaveResult.CONFLICT);
        assertThat(output)
                .contains(
                        "기사 백업 저장 중 조건부 충돌이 발생했습니다.",
                        "backupDate=2026-08-23",
                        "result=CONFLICT"
                )
                .doesNotContain("기사 백업 파일이 이미 존재하여 저장을 건너뜁니다.");
    }

    @Test
    @DisplayName("백업 기준일이 없으면 백업 날짜 검증 예외가 발생한다")
    void failsWhenBackupDateIsNull() {
        assertThatThrownBy(() -> service.backupArticlesByDate(null))
                .isInstanceOfSatisfying(ArticleBackupDateInvalidException.class, e -> {
                    assertThat(e.getApiErrorCode()).isEqualTo(ApiErrorCode.ARTICLE_BACKUP_DATE_INVALID);
                    assertThat(e.getDetails())
                            .containsEntry("field", "backupDate")
                            .containsEntry("reason", "백업 기준일은 필수입니다.");
                });
    }

    @Test
    @DisplayName("예상하지 못한 백업 실패는 백업 실패 예외로 감싼다")
    void wrapsUnexpectedBackupFailure() {
        RuntimeException cause = new RuntimeException("storage failure");
        when(articleService.findArticleBackupItemsByDateRange(BACKUP_DATE, BACKUP_DATE.plusDays(1)))
                .thenReturn(List.of());
        when(converter.serialize(any(ArticleBackupFile.class))).thenReturn(CONTENT);
        when(storage.saveIfAbsent(any(StorageCommand.class))).thenThrow(cause);

        assertThatThrownBy(() -> service.backupArticlesByDate(BACKUP_DATE))
                .isInstanceOfSatisfying(ArticleBackupFailedException.class, e -> {
                    assertThat(e.getApiErrorCode()).isEqualTo(ApiErrorCode.ARTICLE_BACKUP_FAILED);
                    assertThat(e.getCause()).isSameAs(cause);
                    assertThat(e.getDetails())
                            .containsEntry("backupDate", BACKUP_DATE)
                            .containsEntry("cause", "RuntimeException");
                });
    }

    @Test
    @DisplayName("백업 파일 JSON 예외는 감싸지 않고 그대로 전파한다")
    void rethrowsArticleException() {
        ArticleBackupFileJsonException cause = new ArticleBackupFileJsonException(
                "직렬화",
                new RuntimeException("json failure")
        );
        when(articleService.findArticleBackupItemsByDateRange(BACKUP_DATE, BACKUP_DATE.plusDays(1)))
                .thenReturn(List.of());
        when(converter.serialize(any(ArticleBackupFile.class))).thenThrow(cause);

        assertThatThrownBy(() -> service.backupArticlesByDate(BACKUP_DATE))
                .isSameAs(cause);
    }

    private ArticleBackupItem backupItem() {
        return new ArticleBackupItem(
                UUID.fromString("00000000-0000-4000-8000-000000000001"),
                ArticleSource.NAVER,
                "https://example.com/news/1",
                "기사 제목",
                "기사 요약",
                LocalDateTime.of(2026, 8, 23, 10, 15),
                null
        );
    }
}
