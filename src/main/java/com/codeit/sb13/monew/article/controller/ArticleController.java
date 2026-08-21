package com.codeit.sb13.monew.article.controller;

import com.codeit.sb13.monew.article.service.dto.ArticleDto;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.ArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
@Tag(name = "뉴스 기사 관리", description = "뉴스 기사 관련 API")
public class ArticleController {

    private static final String USER_ID_HEADER = "Monew-Request-User-ID";

    private final ArticleService articleService;

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