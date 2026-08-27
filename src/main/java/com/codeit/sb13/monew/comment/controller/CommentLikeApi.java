package com.codeit.sb13.monew.comment.controller;

import com.codeit.sb13.monew.comment.service.dto.CommentLikeDto;

import com.codeit.sb13.monew.global.dto.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "댓글 관리", description = "댓글 관련 API")
public interface CommentLikeApi {

  @Operation(
      summary = "댓글 좋아요 등록",
      description = "사용자가 특정 댓글에 좋아요를 누르는 기능입니다. 같은 사용자는 같은 댓글에 중복 좋아요를 누를 수 없습니다."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "댓글 좋아요 등록 성공",
          content = @Content(schema = @Schema(implementation = CommentLikeDto.class))
      ),
      @ApiResponse(
          responseCode = "404",
          description = "댓글 또는 사용자 정보를 찾을 수 없음",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
      ),
      @ApiResponse(
          responseCode = "500",
          description = "서버 내부 오류 발생",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
      )
  })
  ResponseEntity<CommentLikeDto> likeComment(
      @Parameter(
          name = "commentId",
          description = "좋아요를 누를 댓글의 ID",
          required = true
      ) @PathVariable UUID commentId,
      @Parameter(
          description = "좋아요를 누른 요청자 ID",
          required = true
      ) @RequestHeader("Monew-Request-User-ID") UUID requestUserId
  );


  @Operation(
      summary = "댓글 좋아요 취소",
      description = "사용자가 특정 댓글에 좋아요를 취소합니다."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "댓글 좋아요 취소 성공"
      ),
      @ApiResponse(
          responseCode = "404",
          description = "댓글, 사용자 또는 댓글 좋아요 정보를 찾을 수 없음",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
      ),
      @ApiResponse(
          responseCode = "500",
          description = "서버 내부 오류 발생",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
      )
  })
  ResponseEntity<Void> unlikeComment(
      @Parameter(
          name = "commentId",
          description = "좋아요를 취소할 댓글의 ID",
          required = true
      ) @PathVariable UUID commentId,
      @Parameter(
          description = "좋아요를 취소한 요청자 ID",
          required = true
      ) @RequestHeader("Monew-Request-User-ID") UUID requestUserId);
}
