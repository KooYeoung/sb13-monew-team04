package com.codeit.sb13.monew.activity.controller;

import com.codeit.sb13.monew.activity.service.UserActivityService;
import com.codeit.sb13.monew.activity.service.dto.UserActivityDto;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user-activities")
@RequiredArgsConstructor
public class UserActivityController implements UserActivityApi {
    private final UserActivityService userActivityService;

    @Override
    @GetMapping("/{userId}")
    public ResponseEntity<UserActivityDto> getUserActivity(@PathVariable UUID userId) {
        return ResponseEntity.ok(userActivityService.userActivity(userId));
    }
}
