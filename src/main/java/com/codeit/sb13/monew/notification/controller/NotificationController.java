package com.codeit.sb13.monew.notification.controller;

import com.codeit.sb13.monew.global.MonewHttpHeaders;
import com.codeit.sb13.monew.global.dto.CursorPageResponseDto;
import com.codeit.sb13.monew.notification.controller.dto.NotificationFindRequest;
import com.codeit.sb13.monew.notification.controller.dto.NotificationResponse;
import com.codeit.sb13.monew.notification.mapper.NotificationMapper;
import com.codeit.sb13.monew.notification.service.NotificationService;
import com.codeit.sb13.monew.notification.service.dto.NotificationFindDto;
import com.codeit.sb13.monew.notification.service.dto.NotificationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController implements NotificationApi {

    private final NotificationService notificationService;
    private final NotificationMapper mapper;

    @Override
    @GetMapping
    public ResponseEntity<CursorPageResponseDto<NotificationResponse>> findAllNotifications(
            @ModelAttribute NotificationFindRequest request,
            @RequestHeader(MonewHttpHeaders.REQUEST_USER_ID) UUID userId
    ) {
        NotificationFindDto command = NotificationFindDto.of(
                request.cursor(), request.after(), request.limit(), userId);

        CursorPageResponseDto<NotificationResult> result = notificationService.findAllNotifications(command);
        return ResponseEntity.ok(mapper.toResponse(result));
    }

    @Override
    @PatchMapping("/{notificationId}")
    public ResponseEntity<NotificationResponse> confirmNotification(@PathVariable UUID notificationId,
                                                                    @RequestHeader(MonewHttpHeaders.REQUEST_USER_ID) UUID userId) {
        NotificationResult notificationResult = notificationService.confirmNotification(notificationId, userId);
        return ResponseEntity.ok(mapper.toResponse(notificationResult));
    }

    @Override
    @PatchMapping
    public ResponseEntity<List<NotificationResponse>> confirmAllNotifications(@RequestHeader(MonewHttpHeaders.REQUEST_USER_ID) UUID userId) {
        List<NotificationResult> notificationResults = notificationService.confirmAllNotifications(userId);
        return ResponseEntity.ok(notificationResults.stream().map(mapper::toResponse).toList());
    }
}
