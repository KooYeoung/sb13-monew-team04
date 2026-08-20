package com.codeit.sb13.monew.notification.mapper;

import com.codeit.sb13.monew.notification.controller.dto.NotificationResponse;
import com.codeit.sb13.monew.notification.domain.Notification;
import com.codeit.sb13.monew.notification.service.dto.NotificationResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(source = "user.id", target = "userId")
    NotificationResult toResult(Notification notification);

    NotificationResponse toResponse(NotificationResult result);
}