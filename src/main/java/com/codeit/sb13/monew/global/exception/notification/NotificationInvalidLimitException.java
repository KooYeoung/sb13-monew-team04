package com.codeit.sb13.monew.global.exception.notification;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;

import java.util.Map;

public class NotificationInvalidLimitException extends NotificationException{

    public NotificationInvalidLimitException(int limit) {
        super(ApiErrorCode.NOTIFICATION_INVALID_LIMIT, Map.of("limit", limit));
    }
}
