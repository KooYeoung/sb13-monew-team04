package com.codeit.sb13.monew.article.s3.service;

import com.codeit.sb13.monew.article.s3.service.dto.ArticleBackupFile;
import com.codeit.sb13.monew.article.s3.service.dto.ArticleRestoreCommand;
import com.codeit.sb13.monew.article.s3.service.dto.ArticleRestoreResult;
import com.codeit.sb13.monew.article.s3.service.dto.StorageSearchCommand;
import com.codeit.sb13.monew.global.exception.article.ArticleRestoreDateInvalidException;
import com.codeit.sb13.monew.global.exception.article.ArticleRestoreFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleRestoreService {

    private static final long MAX_RESTORE_DAYS = 31L;

    private final Storage storage;
    private final ArticleBackupFileJsonConverter converter;
    private final ArticleRestoreCommandService commandService;

    public List<ArticleRestoreResult> restoreArticles(ArticleRestoreCommand command) {
        validateRestoreCommand(command);

        LocalDate fromInclusive = command.from();
        LocalDate toInclusive = command.to();

        return Stream.concat(fromInclusive.datesUntil(toInclusive), Stream.of(toInclusive))
                .map(this::restoreDate)
                .toList();
    }

    private ArticleRestoreResult restoreDate(LocalDate restoreDate) {
        StorageSearchCommand searchCommand = new StorageSearchCommand(restoreDate);
        String backupKey = null;
        try {
            backupKey = storage.resolveBackupObjectKey(searchCommand);
            Optional<String> backupJson = storage.find(searchCommand);
            if (backupJson.isEmpty()) {
                log.warn("기사 복구 백업 파일이 없어 복구를 건너뜁니다. restoreDate={}, key={}", restoreDate, backupKey);
                return ArticleRestoreResult.empty(restoreDate);
            }

            ArticleBackupFile backupFile = converter.deserialize(backupJson.get());
            return commandService.restore(restoreDate, backupFile.articles());
        } catch (ArticleRestoreFailedException e) {
            logRestoreFailure(restoreDate, backupKey, e);
            throw e;
        } catch (RuntimeException e) {
            logRestoreFailure(restoreDate, backupKey, e);
            throw new ArticleRestoreFailedException(restoreDate, e);
        }
    }

    private void validateRestoreCommand(ArticleRestoreCommand command) {
        if (command == null) {
            throw new ArticleRestoreDateInvalidException(null, null, "복구 날짜 범위는 필수입니다.");
        }
        validateDateRange(command.from(), command.to());
    }

    private void validateDateRange(LocalDate fromInclusive, LocalDate toInclusive) {
        if (fromInclusive == null || toInclusive == null) {
            throw new ArticleRestoreDateInvalidException(fromInclusive, toInclusive, "복구 날짜 범위는 필수입니다.");
        }
        if (fromInclusive.isAfter(toInclusive)) {
            throw new ArticleRestoreDateInvalidException(fromInclusive, toInclusive, "복구 시작일은 종료일보다 이후일 수 없습니다.");
        }
        long restoreDays = ChronoUnit.DAYS.between(fromInclusive, toInclusive) + 1;
        if (restoreDays > MAX_RESTORE_DAYS) {
            throw new ArticleRestoreDateInvalidException(fromInclusive, toInclusive, "복구 날짜 범위는 최대 31일까지 가능합니다.");
        }
    }

    private void logRestoreFailure(LocalDate restoreDate, String backupKey, RuntimeException e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        log.error(
                "기사 복구에 실패했습니다. restoreDate={}, key={}, causeType={}, causeMessage={}",
                restoreDate,
                backupKey,
                cause.getClass().getSimpleName(),
                cause.getMessage()
        );
    }

}
