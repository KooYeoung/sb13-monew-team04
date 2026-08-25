package com.codeit.sb13.monew.activity.service;

import com.codeit.sb13.monew.activity.service.dto.UserActivityDto;

import java.util.UUID;

public interface UserActivityService {
    UserActivityDto userActivity(UUID userId);
}
