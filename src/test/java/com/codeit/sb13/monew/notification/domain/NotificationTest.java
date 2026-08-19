package com.codeit.sb13.monew.notification.domain;

import com.codeit.sb13.monew.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTest {
    
    @Test
    @DisplayName("알림을 생성하면 confirmed는 기본값으로 false로 설정된다.")
    void 알림_생성() {
        // given
        User user = User.builder()
                .email("test@test.com")
                .nickname("테스트")
                .password("pw")
                .build();
        UUID resourceId = UUID.randomUUID();

        // when
        Notification notification = Notification.create(user, "유저 생성 테스트", resourceId, ResourceType.COMMENT);

        // then
        assertThat(notification.isConfirmed()).isFalse();
        assertThat(notification.getUser()).isEqualTo(user);
        assertThat(notification.getContent()).isEqualTo("유저 생성 테스트");
        assertThat(notification.getResourceType()).isEqualTo(ResourceType.COMMENT);
        assertThat(notification.getResourceId()).isEqualTo(resourceId);
    }
}