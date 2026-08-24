package com.codeit.sb13.monew.article.service.impl;

import com.codeit.sb13.monew.article.repository.dto.ArticleSearchCondition;
import com.codeit.sb13.monew.article.s3.service.dto.ArticleBackupItem;
import com.codeit.sb13.monew.article.service.dto.ArticleDto;
import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.mapper.ArticleMapper;
import com.codeit.sb13.monew.article.repository.ArticleRepository;
import com.codeit.sb13.monew.article.repository.ArticleViewRepository;
import com.codeit.sb13.monew.article.service.ArticleService;
import com.codeit.sb13.monew.article.service.dto.ArticleRequest;
import com.codeit.sb13.monew.article.service.dto.ArticleSearchCommand;
import com.codeit.sb13.monew.global.exception.article.ArticleNotFoundException;
import com.codeit.sb13.monew.global.exception.article.ArticleDuplicateException;
import com.codeit.sb13.monew.global.exception.article.ArticleBackupDateInvalidException;
import com.codeit.sb13.monew.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArticleServiceImpl implements ArticleService {

    private final ArticleRepository articleRepository;
    private final ArticleViewRepository articleViewRepository;
    private final ArticleMapper articleMapper;
    private final UserService userService;

    @Override
    public List<Article> findAll() {
        return articleRepository.findAllByDeletedAtIsNullOrderByDateDesc();
    }

    @Override
    public Article findById(UUID id) {
        return articleRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ArticleNotFoundException(id));
    }

    @Override
    public ArticleDto getArticle(UUID articleId, UUID requestUserId) {
        userService.validateExists(requestUserId);

        Article article = findById(articleId);

        boolean viewedByMe = articleViewRepository
                .existsByArticle_IdAndUser_Id(articleId, requestUserId);
        long viewCount = articleViewRepository.countByArticle_IdAndUser_DeletedAtIsNull(articleId);

        // commentCount는 댓글 파트 집계 방식 확정 전까지 0 (MID4-147)
        return articleMapper.toDto(article, viewedByMe, 0L, viewCount);
    }

    @Override
    public List<ArticleSource> getSources() {
        return List.of(ArticleSource.values());
    }

    @Override
    public List<ArticleDto> searchArticles(ArticleSearchCommand command) {
        userService.validateExists(command.requestUserId());

        ArticleSearchCondition condition = new ArticleSearchCondition(
                command.keyword(),
                command.sourceIn(),
                command.publishDateFrom(),
                command.publishDateTo(),
                command.requestUserId()
        );

        // commentCount는 댓글 집계 방식 확정 전까지 0 (MID4-163 → MID4-147)
        return articleRepository.search(condition).stream()
                .map(row -> articleMapper.toDto(row.article(), row.viewedByMe(), 0L, row.viewCount()))
                .toList();
    }

    /**
     * 엔티티 저장 (내부용)
     */
    @Override
    @Transactional
    public Article save(Article article) {
        return articleRepository.save(article);
    }

    /**
     * DTO로 새로운 기사 생성
     */
    @Override
    @Transactional
    public Article create(ArticleRequest request) {
        // link 중복 체크
        if (articleRepository.findByLink(request.getLink()).isPresent()) {
            throw new ArticleDuplicateException();
        }

        // Article 생성
        Article article = Article.create(
                request.getTitle(),
                request.getSummary(),
                request.getLink(),
                request.getDate(),
                request.getSource()
        );

        // DataIntegrityViolationException 처리 (동시성 문제 대응)
        try {
            return articleRepository.saveAndFlush(article);
        } catch (DataIntegrityViolationException e) {
            if (isLinkUniqueViolation(e)) {
                throw new ArticleDuplicateException();
            }
            throw e;
        }
    }

    @Override
    @Transactional
    public void softDelete(UUID id) {
        Article article = findById(id);
        article.softDelete();
        articleRepository.save(article);
    }

    private boolean isLinkUniqueViolation(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains("uk_articles_link");
    }

    @Override
    public List<ArticleBackupItem> findArticleBackupItemsByDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ArticleBackupDateInvalidException(from, to, "백업 조회 날짜 범위는 필수입니다.");
        }
        if (!from.isBefore(to)) {
            throw new ArticleBackupDateInvalidException(from, to, "백업 조회 시작일은 종료일보다 이전이어야 합니다.");
        }
        return articleRepository.findArticlesForBackup(from.atStartOfDay(), to.atStartOfDay())
                .stream()
                .map(ArticleBackupItem::from)
                .toList();
    }
}
