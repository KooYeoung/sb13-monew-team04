package com.codeit.sb13.monew.notification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTest {
    
    @Test
    @DisplayName("알림을 생성하면 confirmed는 기본값으로 false로 설정된다.")
    void 알림_생성() {
        // given
        UUID user = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        // when
        Notification notification = Notification.create(user, "유저 생성 테스트", resourceId, ResourceType.COMMENT);

        // then
        assertThat(notification.isConfirmed()).isFalse();
    }
}