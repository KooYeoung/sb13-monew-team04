package com.codeit.sb13.monew.article.s3.service;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.repository.ArticleRepository;
import com.codeit.sb13.monew.article.s3.service.dto.ArticleBackupItem;
import com.codeit.sb13.monew.article.s3.service.dto.ArticleRestoreResult;
import com.codeit.sb13.monew.global.exception.article.ArticleRestoreFailedException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArticleRestoreCommandService {

    private final ArticleRepository articleRepository;
    private final ArticleRestoreSaveService articleRestoreSaveService;

    @Transactional
    public ArticleRestoreResult restore(LocalDate restoreDate, List<ArticleBackupItem> items) {
        List<UUID> restoredArticleIds = items.stream()
                .map(item -> restoreItem(restoreDate, item))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        return ArticleRestoreResult.of(restoreDate, restoredArticleIds);
    }

    private Optional<UUID> restoreItem(LocalDate restoreDate, ArticleBackupItem item) {
        if (articleRepository.findByLink(item.link()).isPresent()) {
            return Optional.empty();
        }

        Article article = Article.create(
                item.title(),
                item.summary(),
                item.link(),
                item.publishedAt(),
                item.source()
        );

        try {
            Article savedArticle = articleRestoreSaveService.save(article);
            return Optional.of(savedArticle.getId());
        } catch (DataIntegrityViolationException e) {
            if (articleRepository.findByLink(item.link()).isPresent()) {
                return Optional.empty();
            }
            throw new ArticleRestoreFailedException(restoreDate, e);
        }
    }
}
