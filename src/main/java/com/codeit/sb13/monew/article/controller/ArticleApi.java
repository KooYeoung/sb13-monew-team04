package com.codeit.sb13.monew.article.controller;

import com.codeit.sb13.monew.article.controller.dto.ArticleSearchRequest;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.ArticleDto;
import com.codeit.sb13.monew.article.service.dto.ArticleViewDto;
import com.codeit.sb13.monew.global.MonewHttpHeaders;
import com.codeit.sb13.monew.global.dto.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.UUID;

@Tag(name = "뉴스 기사 관리", description = "뉴스 기사 관련 API")
public interface ArticleApi {

    @Operation(
            summary = "뉴스 기사 목록 조회",
            description = "검색어, 출처, 발행일 범위로 뉴스 기사 목록을 조회합니다. 논리 삭제된 기사는 제외됩니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ArticleDto.class)))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 형식 오류 (헤더 누락, 잘못된 파라미터 형식)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "요청자 정보 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    ResponseEntity<List<ArticleDto>> getArticles(
            @ModelAttribute ArticleSearchRequest request,
            @Parameter(
                    name = MonewHttpHeaders.REQUEST_USER_ID,
                    description = "요청자 ID",
                    required = true
            ) @RequestHeader(MonewHttpHeaders.REQUEST_USER_ID) UUID requestUserId
    );

    @Operation(
            summary = "뉴스 기사 단건 조회",
            description = "뉴스 기사 ID로 뉴스 기사 단건을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = ArticleDto.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 형식 오류 (헤더 누락, 잘못된 UUID)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "뉴스 기사 정보 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    ResponseEntity<ArticleDto> getArticle(
            @Parameter(name = "articleId", description = "뉴스 기사 ID", required = true)
            @PathVariable UUID articleId,
            @Parameter(
                    name = MonewHttpHeaders.REQUEST_USER_ID,
                    description = "요청자 ID",
                    required = true
            ) @RequestHeader(MonewHttpHeaders.REQUEST_USER_ID) UUID requestUserId
    );

    @Operation(
            summary = "뉴스 기사 뷰 등록",
            description = "사용자의 뉴스 기사 조회 기록을 등록합니다. 이미 조회한 기사는 조회 시각만 갱신됩니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "등록 성공",
                    content = @Content(schema = @Schema(implementation = ArticleViewDto.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 형식 오류 (헤더 누락, 잘못된 UUID)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "뉴스 기사 또는 사용자 정보 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "동시 요청 충돌 후 기존 조회 기록 재조회에도 실패함",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    ResponseEntity<ArticleViewDto> registerArticleView(
            @Parameter(name = "articleId", description = "뉴스 기사 ID", required = true)
            @PathVariable UUID articleId,
            @Parameter(
                    name = MonewHttpHeaders.REQUEST_USER_ID,
                    description = "요청자 ID",
                    required = true
            ) @RequestHeader(MonewHttpHeaders.REQUEST_USER_ID) UUID requestUserId
    );

    @Operation(summary = "출처 목록 조회", description = "출처 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ArticleSource.class)))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    ResponseEntity<List<ArticleSource>> getSources();
}