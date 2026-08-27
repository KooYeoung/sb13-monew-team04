package com.codeit.sb13.monew.notification.controller;

import com.codeit.sb13.monew.global.MonewHttpHeaders;
import com.codeit.sb13.monew.global.dto.ApiErrorResponse;
import com.codeit.sb13.monew.global.dto.CursorPageResponseDto;
import com.codeit.sb13.monew.notification.controller.dto.NotificationFindRequest;
import com.codeit.sb13.monew.notification.controller.dto.NotificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "알림 관리", description = "알림 관련 API")
public interface NotificationApi {

    @Operation(
            summary = "알림 목록 조회",
            description = "요청자의 확인하지 않은 알림 목록을 커서 페이지네이션으로 조회합니다."
    )
    @Parameters({
            @Parameter(name = "cursor", description = "이전 페이지 마지막 알림의 ID. 첫 페이지 조회 시 생략"),
            @Parameter(name = "after", description = "이전 페이지 마지막 알림의 생성 시각. 첫 페이지 조회 시 생략"),
            @Parameter(name = "limit", description = "조회할 최대 개수", required = true),
            @Parameter(name = MonewHttpHeaders.REQUEST_USER_ID, description = "요청자 ID", required = true)
    })
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "알림 목록 조회 성공. content 필드는 NotificationResponse 배열입니다."),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 형식 오류 (헤더 누락, cursor/limit 값 오류)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "요청자 정보 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<CursorPageResponseDto<NotificationResponse>> findAllNotifications(
            @ModelAttribute NotificationFindRequest request,
            @Parameter(
                    name = MonewHttpHeaders.REQUEST_USER_ID,
                    description = "요청자 ID",
                    required = true
            ) @RequestHeader(MonewHttpHeaders.REQUEST_USER_ID) UUID userId
    );

    @Operation(
            summary = "알림 확인 처리",
            description = "알림 하나를 확인(읽음) 처리합니다. 요청자 소유가 아닌 알림 ID는 존재하지 않는 알림과 동일하게 404로 응답합니다."
    )
    @Parameters({
            @Parameter(name = "notificationId", description = "확인 처리할 알림 ID", required = true),
            @Parameter(name = MonewHttpHeaders.REQUEST_USER_ID, description = "요청자 ID", required = true)
    })
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "확인 처리 성공",
                    content = @Content(schema = @Schema(implementation = NotificationResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 형식 오류 (잘못된 UUID, 헤더 누락)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "요청자 정보 없음 또는 알림 정보 없음(타 사용자 소유 알림 포함)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<NotificationResponse> confirmNotification(
            @PathVariable UUID notificationId,
            @Parameter(
                    name = MonewHttpHeaders.REQUEST_USER_ID,
                    description = "요청자 ID",
                    required = true
            ) @RequestHeader(MonewHttpHeaders.REQUEST_USER_ID) UUID userId
    );

    @Operation(
            summary = "전체 알림 확인 처리",
            description = "요청자의 확인하지 않은 알림을 모두 확인(읽음) 처리합니다."
    )
    @Parameters({
            @Parameter(name = MonewHttpHeaders.REQUEST_USER_ID, description = "요청자 ID", required = true)
    })
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "전체 확인 처리 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = NotificationResponse.class)))),
            @ApiResponse(
                    responseCode = "400",
                    description = "헤더 누락",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "요청자 정보 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<List<NotificationResponse>> confirmAllNotifications(
            @Parameter(
                    name = MonewHttpHeaders.REQUEST_USER_ID,
                    description = "요청자 ID",
                    required = true
            ) @RequestHeader(MonewHttpHeaders.REQUEST_USER_ID) UUID userId
    );
}
