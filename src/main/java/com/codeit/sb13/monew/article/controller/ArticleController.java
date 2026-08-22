package com.codeit.sb13.monew.article.controller;

import com.codeit.sb13.monew.article.controller.dto.ArticleSearchRequest;
import com.codeit.sb13.monew.article.service.ArticleViewService;
import com.codeit.sb13.monew.article.service.dto.ArticleDto;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.ArticleService;
import com.codeit.sb13.monew.article.service.dto.ArticleSearchCommand;
import com.codeit.sb13.monew.article.service.dto.ArticleViewDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
@Tag(name = "뉴스 기사 관리", description = "뉴스 기사 관련 API")
public class ArticleController {

    private static final String USER_ID_HEADER = "Monew-Request-User-ID";

    private final ArticleService articleService;
    private final ArticleViewService articleViewService;

    @Operation(summary = "뉴스 기사 목록 조회",
            description = "검색어, 출처, 발행일 범위로 뉴스 기사 목록을 조회합니다. 논리 삭제된 기사는 제외됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "요청 형식 오류 (헤더 누락, 잘못된 파라미터 형식)"),
            @ApiResponse(responseCode = "404", description = "요청자 정보 없음"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    @GetMapping
    public ResponseEntity<List<ArticleDto>> getArticles(
            @ModelAttribute ArticleSearchRequest request,
            @Parameter(description = "요청자 ID") @RequestHeader(USER_ID_HEADER) UUID requestUserId
    ) {
        ArticleSearchCommand command = new ArticleSearchCommand(
                request.keyword(),
                request.sourceIn(),
                request.publishDateFrom(),
                request.publishDateTo(),
                requestUserId
        );

        return ResponseEntity.ok(articleService.searchArticles(command));
    }

    @Operation(summary = "뉴스 기사 단건 조회",
            description = "뉴스 기사 ID로 뉴스 기사 단건을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "요청 형식 오류 (헤더 누락, 잘못된 UUID)"),
            @ApiResponse(responseCode = "404", description = "뉴스 기사 정보 없음"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    @GetMapping("/{articleId}")
    public ResponseEntity<ArticleDto> getArticle(
            @Parameter(description = "뉴스 기사 ID") @PathVariable UUID articleId,
            @Parameter(description = "요청자 ID") @RequestHeader(USER_ID_HEADER) UUID requestUserId
    ) {
        return ResponseEntity.ok(articleService.getArticle(articleId, requestUserId));
    }

    @Operation(summary = "뉴스 기사 뷰 등록",
            description = "사용자의 뉴스 기사 조회 기록을 등록합니다. 이미 조회한 기사는 조회 시각만 갱신됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록 성공"),
            @ApiResponse(responseCode = "400", description = "요청 형식 오류 (헤더 누락, 잘못된 UUID)"),
            @ApiResponse(responseCode = "404", description = "뉴스 기사 또는 사용자 정보 없음"),
            @ApiResponse(responseCode = "409", description = "동시 요청으로 조회 기록 등록이 충돌함"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })

    @PostMapping("/{articleId}/article-views")
    public ResponseEntity<ArticleViewDto> registerArticleView(
            @Parameter(description = "뉴스 기사 ID") @PathVariable UUID articleId,
            @Parameter(description = "요청자 ID") @RequestHeader(USER_ID_HEADER) UUID requestUserId
    ) {
        return ResponseEntity.ok(articleViewService.recordView(articleId, requestUserId));
    }

    @Operation(summary = "출처 목록 조회", description = "출처 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })

    @GetMapping("/sources")
    public ResponseEntity<List<ArticleSource>> getSources() {
        return ResponseEntity.ok(articleService.getSources());
    }
}