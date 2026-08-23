package com.codeit.sb13.monew.global.exception.notification;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;

import java.util.Collections;

public class NotificationInvalidCursorException extends NotificationException{

    public NotificationInvalidCursorException(String cursor) {
        super(ApiErrorCode.NOTIFICATION_INVALID_CURSOR, Collections.singletonMap("cursor", cursor));
    }
}
