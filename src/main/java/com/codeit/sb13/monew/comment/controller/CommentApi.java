package com.codeit.sb13.monew.comment.controller;

import com.codeit.sb13.monew.comment.controller.dto.CommentRegisterRequest;
import com.codeit.sb13.monew.comment.controller.dto.CommentSearchRequest;
import com.codeit.sb13.monew.comment.controller.dto.CommentUpdateRequest;
import com.codeit.sb13.monew.comment.service.dto.CommentDto;
import com.codeit.sb13.monew.global.MonewHttpHeaders;
import com.codeit.sb13.monew.global.dto.ApiErrorResponse;
import com.codeit.sb13.monew.global.dto.CursorPageResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "댓글 관리", description = "댓글 관련 API")
public interface CommentApi {

  @Operation(
      summary = "댓글 등록",
      description = "기사별 새 댓글을 등록합니다.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "댓글 등록 성공",
          content = @Content(schema = @Schema(implementation = CommentDto.class))),
      @ApiResponse(
          responseCode = "400",
          description = "잘못된 조회 요청(입력값 검증 실패)",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "500",
          description = "서버 내부 오류",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<CommentDto> createComment(@RequestBody CommentRegisterRequest request);

  @Operation(
      summary = "댓글 목록 조회",
      description = "기사별 댓글을 정렬·커서 페이지네이션으로 조회합니다.")
  @Parameters({
      @Parameter(name = "articleId", description = "기사 ID", required = true),
      @Parameter(name = "orderBy", description = "정렬 기준명", required = true,
          schema = @Schema(allowableValues = {"createdAt", "likeCount"})),
      @Parameter(name = "direction", description = "정렬 방향(ASC/DESC)", required = true,
          schema = @Schema(allowableValues = {"ASC", "DESC"})),
      @Parameter(name = "cursor", description = "이전 페이지 마지막 항목의 주 정렬 값"),
      @Parameter(name = "after", description = "이전 페이지 마지막 항목의 생성 시각"),
      @Parameter(name = "idAfter", description = "동률 해소를 위한 이전 페이지 마지막 댓글 ID"),
      @Parameter(name = "limit", description = "커서 페이지 크기", required = true),
      @Parameter(name = MonewHttpHeaders.REQUEST_USER_ID, description = "요청자 ID", required = true)
  })
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "댓글 목록 조회 성공",
          content = @Content(schema = @Schema(implementation = CursorPageResponseDto.class))),
      @ApiResponse(
          responseCode = "400",
          description = "잘못된 조회 요청(정렬 기준 오류, 페이지네이션 파라미터 오류 등)",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "500",
          description = "서버 내부 오류",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<CursorPageResponseDto<CommentDto>> searchComments(
      @ModelAttribute CommentSearchRequest request,
      @RequestHeader(MonewHttpHeaders.REQUEST_USER_ID) UUID requestUserId
  );


  @Operation(
      summary = "댓글 정보 수정",
      description = "댓글 작성자만 본인의 댓글 내용을 수정할 수 있습니다.")
  @Parameters({
      @Parameter(name = "commentId", description = "댓글 ID", required = true),
      @Parameter(name = MonewHttpHeaders.REQUEST_USER_ID, description = "요청자 ID", required = true)
  })
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "댓글 수정 성공",
          content = @Content(schema = @Schema(implementation = CommentDto.class))),
      @ApiResponse(responseCode = "400", description = "잘못된 요청(입력값 검증 실패)",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(responseCode = "403", description = "댓글 수정 권한 없음",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(responseCode = "404", description = "댓글 정보를 찾을 수 없음",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(responseCode = "500", description = "서버 내부 오류",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<CommentDto> updateComment(
      @PathVariable UUID commentId,
      @RequestHeader(MonewHttpHeaders.REQUEST_USER_ID) UUID requestUserId,
      @RequestBody CommentUpdateRequest request
  );

}
