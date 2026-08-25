package com.codeit.sb13.monew.article.s3.service;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.s3.service.dto.ArticleBackupFile;
import com.codeit.sb13.monew.article.s3.service.dto.ArticleBackupItem;
import com.codeit.sb13.monew.article.s3.service.dto.ArticleRestoreResult;
import com.codeit.sb13.monew.article.s3.service.dto.StorageSearchCommand;
import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import com.codeit.sb13.monew.global.exception.article.ArticleBackupFileJsonException;
import com.codeit.sb13.monew.global.exception.article.ArticleRestoreDateInvalidException;
import com.codeit.sb13.monew.global.exception.article.ArticleRestoreFailedException;
import com.codeit.sb13.monew.global.exception.article.ArticleS3StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ArticleRestoreService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class ArticleRestoreServiceTest {

    private static final LocalDate RESTORE_DATE = LocalDate.of(2026, 8, 23);
    private static final LocalDate NEXT_DATE = LocalDate.of(2026, 8, 24);
    private static final UUID ORIGINAL_ARTICLE_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID RESTORED_ARTICLE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Mock
    private Storage storage;

    @Mock
    private ArticleBackupFileJsonConverter converter;

    @Mock
    private ArticleRestoreCommandService commandService;

    private ArticleRestoreService service;

    @BeforeEach
    void setUp() {
        service = new ArticleRestoreService(storage, converter, commandService);
    }

    @Test
    @DisplayName("from/to inclusive 기준으로 복구 대상 날짜를 순회한다")
    void restoresInclusiveDateRange() {
        when(storage.find(new StorageSearchCommand(RESTORE_DATE))).thenReturn(Optional.empty());
        when(storage.find(new StorageSearchCommand(NEXT_DATE))).thenReturn(Optional.empty());

        List<ArticleRestoreResult> results = service.restoreArticles(RESTORE_DATE, NEXT_DATE);

        assertThat(results)
                .extracting(ArticleRestoreResult::restoreDate)
                .containsExactly(RESTORE_DATE, NEXT_DATE);
        assertThat(results)
                .extracting(ArticleRestoreResult::restoredArticleCount)
                .containsExactly(0L, 0L);
    }

    @Test
    @DisplayName("LocalDate.MAX 단일 날짜도 오버플로 없이 복구 대상으로 처리한다")
    void restoresSingleMaxDateWithoutOverflow() {
        when(storage.find(new StorageSearchCommand(LocalDate.MAX))).thenReturn(Optional.empty());

        List<ArticleRestoreResult> results = service.restoreArticles(LocalDate.MAX, LocalDate.MAX);

        assertThat(results)
                .extracting(ArticleRestoreResult::restoreDate)
                .containsExactly(LocalDate.MAX);
        verify(storage).find(new StorageSearchCommand(LocalDate.MAX));
    }

    @Test
    @DisplayName("LocalDate.MAX로 끝나는 날짜 범위도 순서대로 복구 대상으로 처리한다")
    void restoresRangeEndingAtMaxDateWithoutOverflow() {
        LocalDate maxPreviousDate = LocalDate.MAX.minusDays(1);
        when(storage.find(new StorageSearchCommand(maxPreviousDate))).thenReturn(Optional.empty());
        when(storage.find(new StorageSearchCommand(LocalDate.MAX))).thenReturn(Optional.empty());

        List<ArticleRestoreResult> results = service.restoreArticles(maxPreviousDate, LocalDate.MAX);

        assertThat(results)
                .extracting(ArticleRestoreResult::restoreDate)
                .containsExactly(maxPreviousDate, LocalDate.MAX);
        verify(storage).find(new StorageSearchCommand(maxPreviousDate));
        verify(storage).find(new StorageSearchCommand(LocalDate.MAX));
    }

    @Test
    @DisplayName("복구 날짜 범위가 최대 31일이면 복구 대상으로 처리한다")
    void restoresWhenDateRangeIsExactlyMaxDays() {
        LocalDate from = RESTORE_DATE;
        LocalDate to = from.plusDays(30);
        when(storage.find(any(StorageSearchCommand.class))).thenReturn(Optional.empty());

        List<ArticleRestoreResult> results = service.restoreArticles(from, to);

        assertThat(results).hasSize(31);
        assertThat(results)
                .extracting(ArticleRestoreResult::restoreDate)
                .first()
                .isEqualTo(from);
        assertThat(results)
                .extracting(ArticleRestoreResult::restoreDate)
                .last()
                .isEqualTo(to);
        verify(storage, times(31)).find(any(StorageSearchCommand.class));
    }

    @Test
    @DisplayName("복구 날짜 범위가 31일을 초과하면 실패한다")
    void throwsDateInvalidExceptionWhenDateRangeExceedsMaxDays() {
        LocalDate from = RESTORE_DATE;
        LocalDate to = from.plusDays(31);

        assertThatThrownBy(() -> service.restoreArticles(from, to))
                .isInstanceOfSatisfying(ArticleRestoreDateInvalidException.class, e -> {
                    assertThat(e.getApiErrorCode()).isEqualTo(ApiErrorCode.ARTICLE_RESTORE_DATE_INVALID);
                    assertThat(e.getDetails())
                            .containsEntry("from", from)
                            .containsEntry("to", to)
                            .containsEntry("reason", "복구 날짜 범위는 최대 31일까지 가능합니다.");
                });
        verifyNoInteractions(storage, converter, commandService);
    }

    @Test
    @DisplayName("백업 파일이 없는 날짜는 0건 결과로 처리한다")
    void returnsEmptyResultWhenBackupFileIsMissing() {
        when(storage.find(new StorageSearchCommand(RESTORE_DATE))).thenReturn(Optional.empty());

        List<ArticleRestoreResult> results = service.restoreArticles(RESTORE_DATE, RESTORE_DATE);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).restoreDate()).isEqualTo(RESTORE_DATE);
        assertThat(results.get(0).restoredArticleIds()).isEmpty();
        assertThat(results.get(0).restoredArticleCount()).isZero();
        verify(converter, never()).deserialize(anyString());
        verifyNoInteractions(commandService);
    }

    @Test
    @DisplayName("백업 JSON을 객체화한 뒤 날짜 단위 command service에 위임한다")
    void delegatesRestoreToCommandService() {
        ArticleBackupItem item = backupItem();
        ArticleBackupFile backupFile = ArticleBackupFile.of(
                RESTORE_DATE,
                LocalDateTime.of(2026, 8, 24, 0, 10),
                List.of(item)
        );
        ArticleRestoreResult restoreResult = ArticleRestoreResult.of(
                RESTORE_DATE,
                List.of(RESTORED_ARTICLE_ID)
        );
        when(storage.find(new StorageSearchCommand(RESTORE_DATE))).thenReturn(Optional.of("{}"));
        when(converter.deserialize("{}")).thenReturn(backupFile);
        when(commandService.restore(RESTORE_DATE, backupFile.articles())).thenReturn(restoreResult);

        List<ArticleRestoreResult> results = service.restoreArticles(RESTORE_DATE, RESTORE_DATE);

        assertThat(results).containsExactly(restoreResult);
        verify(commandService).restore(RESTORE_DATE, backupFile.articles());
    }

    @Test
    @DisplayName("복구 날짜 조건이 올바르지 않으면 실패한다")
    void throwsDateInvalidExceptionWhenDateRangeIsInvalid() {
        assertThatThrownBy(() -> service.restoreArticles(null, RESTORE_DATE))
                .isInstanceOfSatisfying(ArticleRestoreDateInvalidException.class, e -> {
                    assertThat(e.getApiErrorCode()).isEqualTo(ApiErrorCode.ARTICLE_RESTORE_DATE_INVALID);
                    assertThat(e.getDetails())
                            .containsEntry("from", null)
                            .containsEntry("to", RESTORE_DATE);
                });

        assertThatThrownBy(() -> service.restoreArticles(RESTORE_DATE, null))
                .isInstanceOfSatisfying(ArticleRestoreDateInvalidException.class, e -> {
                    assertThat(e.getApiErrorCode()).isEqualTo(ApiErrorCode.ARTICLE_RESTORE_DATE_INVALID);
                    assertThat(e.getDetails())
                            .containsEntry("from", RESTORE_DATE)
                            .containsEntry("to", null);
                });

        assertThatThrownBy(() -> service.restoreArticles(NEXT_DATE, RESTORE_DATE))
                .isInstanceOfSatisfying(ArticleRestoreDateInvalidException.class, e -> {
                    assertThat(e.getApiErrorCode()).isEqualTo(ApiErrorCode.ARTICLE_RESTORE_DATE_INVALID);
                    assertThat(e.getDetails())
                            .containsEntry("from", NEXT_DATE)
                            .containsEntry("to", RESTORE_DATE);
                });
        verifyNoInteractions(storage, converter, commandService);
    }

    @Test
    @DisplayName("S3 조회 실패는 복구 실패 예외로 감싼다")
    void wrapsStorageFailure() {
        ArticleS3StorageException cause = new ArticleS3StorageException(
                "getObject",
                "monew-backup",
                "article-backups/2026-08-23.json",
                SdkClientException.builder().message("client failure").build()
        );
        when(storage.find(new StorageSearchCommand(RESTORE_DATE))).thenThrow(cause);

        assertThatThrownBy(() -> service.restoreArticles(RESTORE_DATE, RESTORE_DATE))
                .isInstanceOfSatisfying(ArticleRestoreFailedException.class, e -> {
                    assertThat(e.getApiErrorCode()).isEqualTo(ApiErrorCode.ARTICLE_RESTORE_FAILED);
                    assertThat(e.getCause()).isSameAs(cause);
                });
    }

    @Test
    @DisplayName("백업 JSON 변환 실패는 복구 실패 예외로 감싼다")
    void wrapsJsonFailure() {
        ArticleBackupFileJsonException cause = new ArticleBackupFileJsonException(
                "역직렬화",
                new IllegalArgumentException("invalid json")
        );
        when(storage.find(new StorageSearchCommand(RESTORE_DATE))).thenReturn(Optional.of("{}"));
        when(converter.deserialize("{}")).thenThrow(cause);

        assertThatThrownBy(() -> service.restoreArticles(RESTORE_DATE, RESTORE_DATE))
                .isInstanceOfSatisfying(ArticleRestoreFailedException.class, e -> {
                    assertThat(e.getApiErrorCode()).isEqualTo(ApiErrorCode.ARTICLE_RESTORE_FAILED);
                    assertThat(e.getCause()).isSameAs(cause);
                });
    }

    @Test
    @DisplayName("command service의 복구 실패 예외는 그대로 전파한다")
    void rethrowsCommandServiceRestoreFailure() {
        ArticleBackupFile backupFile = ArticleBackupFile.of(
                RESTORE_DATE,
                LocalDateTime.of(2026, 8, 24, 0, 10),
                List.of(backupItem())
        );
        ArticleRestoreFailedException cause = new ArticleRestoreFailedException(
                RESTORE_DATE,
                new IllegalStateException("restore failure")
        );
        when(storage.find(new StorageSearchCommand(RESTORE_DATE))).thenReturn(Optional.of("{}"));
        when(converter.deserialize("{}")).thenReturn(backupFile);
        when(commandService.restore(RESTORE_DATE, backupFile.articles())).thenThrow(cause);

        assertThatThrownBy(() -> service.restoreArticles(RESTORE_DATE, RESTORE_DATE))
                .isSameAs(cause);
    }

    private ArticleBackupItem backupItem() {
        return new ArticleBackupItem(
                ORIGINAL_ARTICLE_ID,
                ArticleSource.NAVER,
                "https://example.com/news/1",
                "복구 기사 제목",
                "복구 기사 요약",
                LocalDateTime.of(2026, 8, 23, 10, 15),
                null
        );
    }
}
