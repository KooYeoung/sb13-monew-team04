package com.codeit.sb13.monew.activity.controller;

import com.codeit.sb13.monew.activity.service.dto.UserActivityDto;
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

@Tag(name = "활동내역 관리", description = "사용자 활동내역 관련 API")
public interface UserActivityApi {

    @Operation(
            summary = "사용자 활동내역 조회",
            description = "사용자 ID로 최근 구독 관심사, 작성 댓글, 좋아요한 댓글, 조회 기사를 포함한 활동내역을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "활동내역 조회 성공",
                    content = @Content(schema = @Schema(implementation = UserActivityDto.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청(유효하지 않은 사용자 ID 형식)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "사용자 정보를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    ResponseEntity<UserActivityDto> getUserActivity(
            @Parameter(name = "userId", description = "사용자 ID", required = true)
            @PathVariable UUID userId
    );
}
