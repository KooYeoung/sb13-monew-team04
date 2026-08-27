package com.codeit.sb13.monew.article.controller;

import com.codeit.sb13.monew.article.controller.dto.ArticleRestoreRequest;
import com.codeit.sb13.monew.article.controller.dto.ArticleSearchRequest;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.s3.service.ArticleRestoreService;
import com.codeit.sb13.monew.article.s3.service.dto.ArticleRestoreResult;
import com.codeit.sb13.monew.article.service.ArticleService;
import com.codeit.sb13.monew.article.service.ArticleViewService;
import com.codeit.sb13.monew.article.service.dto.ArticleDto;
import com.codeit.sb13.monew.article.service.dto.ArticleSearchCommand;
import com.codeit.sb13.monew.article.service.dto.ArticleViewDto;
import com.codeit.sb13.monew.global.MonewHttpHeaders;
import com.codeit.sb13.monew.global.dto.CursorPageResponseDto;
import com.codeit.sb13.monew.global.exception.article.ArticleSearchConditionInvalidException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class ArticleController implements ArticleApi {

    /**
     * 집계값 정렬은 기사 한 건마다 상관 서브쿼리를 평가한다. 상한이 없으면
     * limit=1000000 같은 요청이 그대로 리포지토리에 도달해 DB에 과부하를 준다.
     */
    private static final int MAX_LIMIT = 100;

    private final ArticleService articleService;
    private final ArticleViewService articleViewService;
    private final ArticleRestoreService articleRestoreService;

    @Override
    @GetMapping
    public ResponseEntity<CursorPageResponseDto<ArticleDto>> getArticles(
            @ModelAttribute ArticleSearchRequest request,
            @RequestHeader(MonewHttpHeaders.REQUEST_USER_ID) UUID requestUserId
    ) {
        if (request.orderBy() == null) {
            throw new ArticleSearchConditionInvalidException("orderBy는 필수입니다.");
        }
        if (request.direction() == null) {
            throw new ArticleSearchConditionInvalidException("direction은 필수입니다.");
        }
        if (request.limit() < 1) {
            throw new ArticleSearchConditionInvalidException("limit은 1 이상이어야 합니다: " + request.limit());
        }
        if (request.limit() > MAX_LIMIT) {
            throw new ArticleSearchConditionInvalidException(
                    "limit은 " + MAX_LIMIT + " 이하여야 합니다: " + request.limit());
        }

        ArticleSearchCommand command = new ArticleSearchCommand(
                request.keyword(),
                request.interestId(),
                request.sourceIn(),
                request.publishDateFrom(),
                request.publishDateTo(),
                request.orderBy(),
                request.direction(),
                request.cursor(),
                request.after(),
                request.limit(),
                requestUserId
        );

        return ResponseEntity.ok(articleService.searchArticles(command));
    }

    @Override
    @GetMapping("/{articleId}")
    public ResponseEntity<ArticleDto> getArticle(
            @PathVariable UUID articleId,
            @RequestHeader(MonewHttpHeaders.REQUEST_USER_ID) UUID requestUserId
    ) {
        return ResponseEntity.ok(articleService.getArticle(articleId, requestUserId));
    }

    @Override
    @PostMapping("/{articleId}/article-views")
    public ResponseEntity<ArticleViewDto> registerArticleView(
            @PathVariable UUID articleId,
            @RequestHeader(MonewHttpHeaders.REQUEST_USER_ID) UUID requestUserId
    ) {
        return ResponseEntity.ok(articleViewService.recordView(articleId, requestUserId));
    }

    @Override
    @GetMapping("/sources")
    public ResponseEntity<List<ArticleSource>> getSources() {
        return ResponseEntity.ok(articleService.getSources());
    }

    @Override
    @GetMapping("/restore")
    public ResponseEntity<List<ArticleRestoreResult>> restore(@Valid @ModelAttribute ArticleRestoreRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(articleRestoreService.restoreArticles(request.toRestoreCommand()));
    }

    @Override
    @DeleteMapping("/{articleId}")
    public ResponseEntity<Void> softDeleteArticle(@PathVariable UUID articleId) {
        articleService.softDelete(articleId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/{articleId}/hard")
    public ResponseEntity<Void> hardDeleteArticle(@PathVariable UUID articleId) {
        articleService.hardDelete(articleId);
        return ResponseEntity.noContent().build();
    }
}
