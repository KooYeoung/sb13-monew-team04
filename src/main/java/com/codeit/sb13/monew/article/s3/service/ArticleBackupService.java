package com.codeit.sb13.monew.article.s3.service;

import com.codeit.sb13.monew.article.s3.service.dto.ArticleBackupFile;
import com.codeit.sb13.monew.article.s3.service.dto.ArticleBackupItem;
import com.codeit.sb13.monew.article.s3.service.dto.StorageCommand;
import com.codeit.sb13.monew.article.s3.service.dto.StorageSaveResult;
import com.codeit.sb13.monew.article.service.ArticleService;
import com.codeit.sb13.monew.global.exception.article.ArticleBackupDateInvalidException;
import com.codeit.sb13.monew.global.exception.article.ArticleBackupFailedException;
import com.codeit.sb13.monew.global.exception.article.ArticleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleBackupService {

    private final ArticleService articleService;
    private final ArticleBackupFileJsonConverter converter;
    private final Storage storage;

    public StorageSaveResult backupPreviousDayArticles() {
        return backupArticlesByDate(LocalDate.now().minusDays(1));
    }

    public StorageSaveResult backupArticlesByDate(LocalDate backupDate) {
        try {
            if (backupDate == null) {
                throw new ArticleBackupDateInvalidException("backupDate", "백업 기준일은 필수입니다.");
            }

            List<ArticleBackupItem> articles = articleService.findArticleBackupItemsByDateRange(
                    backupDate,
                    backupDate.plusDays(1)
            );
            String content = converter.serialize(ArticleBackupFile.of(backupDate, LocalDateTime.now(), articles));
            StorageSaveResult result = storage.saveIfAbsent(StorageCommand.of(backupDate, content));

            logBackupResult(backupDate, result, articles.size());
            return result;
        } catch (ArticleException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ArticleBackupFailedException(backupDate, e);
        }
    }

    private void logBackupResult(LocalDate backupDate, StorageSaveResult result, long articlesSize) {
        if (result == StorageSaveResult.SAVED) {
            log.info("기사 백업을 완료했습니다. backupDate={}, articleCount={}", backupDate, articlesSize);
            return;
        }

        if (result == StorageSaveResult.ALREADY_EXISTS) {
            log.info("기사 백업 파일이 이미 존재하여 저장을 건너뜁니다. backupDate={}, result={}", backupDate, result);
            return;
        }

        log.warn("기사 백업 저장 중 조건부 충돌이 발생했습니다. backupDate={}, result={}", backupDate, result);
    }
}
