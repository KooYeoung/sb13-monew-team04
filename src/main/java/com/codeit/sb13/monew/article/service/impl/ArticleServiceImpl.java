package com.codeit.sb13.monew.article.service.impl;

import com.codeit.sb13.monew.article.repository.dto.ArticleSearchCondition;
import com.codeit.sb13.monew.article.repository.dto.ArticleSearchPage;
import com.codeit.sb13.monew.article.repository.dto.ArticleSearchRow;
import com.codeit.sb13.monew.article.s3.service.dto.ArticleBackupItem;
import com.codeit.sb13.monew.article.service.dto.ArticleDto;
import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.mapper.ArticleMapper;
import com.codeit.sb13.monew.article.repository.ArticleRepository;
import com.codeit.sb13.monew.article.repository.ArticleViewRepository;
import com.codeit.sb13.monew.article.service.ArticleService;
import com.codeit.sb13.monew.article.service.dto.ArticleOrderBy;
import com.codeit.sb13.monew.article.service.dto.ArticleRequest;
import com.codeit.sb13.monew.article.service.dto.ArticleSearchCommand;
import com.codeit.sb13.monew.comment.repository.CommentLikeRepository;
import com.codeit.sb13.monew.comment.repository.CommentRepository;
import com.codeit.sb13.monew.global.dto.CursorPageResponseDto;
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
    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
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

        long commentCount = commentRepository
                .countByArticle_IdAndDeletedAtIsNullAndUser_DeletedAtIsNull(articleId);

        return articleMapper.toDto(article, viewedByMe, commentCount, viewCount);
    }

    @Override
    public List<ArticleSource> getSources() {
        return List.of(ArticleSource.values());
    }

    @Override
    public CursorPageResponseDto<ArticleDto> searchArticles(ArticleSearchCommand command) {
        userService.validateExists(command.requestUserId());

        ArticleSearchCondition condition = new ArticleSearchCondition(
                command.keyword(),
                command.sourceIn(),
                command.publishDateFrom(),
                command.publishDateTo(),
                command.orderBy(),
                command.direction(),
                command.cursor(),
                command.after(),
                command.idAfter(),
                command.limit(),
                command.requestUserId()
        );

        ArticleSearchPage page = articleRepository.search(condition);

        List<ArticleDto> content = page.rows().stream()
                .map(row -> articleMapper.toDto(
                        row.article(), row.viewedByMe(), row.commentCount(), row.viewCount()))
                .toList();

        return new CursorPageResponseDto<>(
                content,
                nextCursor(page.rows(), command.orderBy()),
                nextAfter(page.rows()),
                nextIdAfter(page.rows()),
                content.size(),
                page.totalElements(),
                page.hasNext()
        );
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

    @Override
    @Transactional
    public void hardDelete(UUID id) {
        if (!articleRepository.existsById(id)) {
            throw new ArticleNotFoundException(id);
        }
        // FK 제약 순서: CommentLike -> Comment -> ArticleView -> Article
        commentLikeRepository.deleteByComment_Article_Id(id);
        commentRepository.deleteByArticle_Id(id);
        articleViewRepository.deleteByArticle_Id(id);
        articleRepository.deleteById(id);
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


    /**
     * 다음 페이지 조회 시 {@code cursor} 파라미터로 돌려보낼 값을 만든다.
     *
     * <p>응답 DTO가 아니라 {@code ArticleSearchRow}에서 뽑는다. keyset의 보조 기준이
     * {@code article.createdAt}인데 {@code ArticleDto}에는 그 필드가 없기 때문이다.
     * 세 커서를 모두 같은 출처에서 만들어야 한 항목의 값끼리 어긋나지 않는다.
     */
    private String nextCursor(List<ArticleSearchRow> rows, ArticleOrderBy orderBy) {
        if (rows.isEmpty()) {
            return null;
        }

        ArticleSearchRow last = rows.get(rows.size() - 1);
        return switch (orderBy) {
            case COMMENT_COUNT -> String.valueOf(last.commentCount());
            case VIEW_COUNT -> String.valueOf(last.viewCount());
            case PUBLISH_DATE -> last.article().getDate().toString();
        };
    }

    private String nextAfter(List<ArticleSearchRow> rows) {
        if (rows.isEmpty()) {
            return null;
        }

        return rows.get(rows.size() - 1).article().getCreatedAt().toString();
    }

    private String nextIdAfter(List<ArticleSearchRow> rows) {
        if (rows.isEmpty()) {
            return null;
        }

        return rows.get(rows.size() - 1).article().getId().toString();
    }
}
