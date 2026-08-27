package com.codeit.sb13.monew.notification.domain;

import com.codeit.sb13.monew.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTest {

    User user;
    UUID resourceId;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("test@test.com")
                .nickname("테스트")
                .password("pw")
                .build();
        resourceId = UUID.randomUUID();
    }
    
    @Test
    @DisplayName("알림을 생성하면 confirmed는 기본값으로 false로 설정된다.")
    void 알림_생성() {
        // given

        // when
        Notification notification = Notification.create(user, "유저 생성 테스트", resourceId, ResourceType.COMMENT);

        // then
        assertThat(notification.isConfirmed()).isFalse();
        assertThat(notification.getUser()).isEqualTo(user);
        assertThat(notification.getContent()).isEqualTo("유저 생성 테스트");
        assertThat(notification.getResourceType()).isEqualTo(ResourceType.COMMENT);
        assertThat(notification.getResourceId()).isEqualTo(resourceId);
    }
    
    @Test
    @DisplayName("알림을 확인하면 confirmed는 true로 변경된다.")
    void 알림_확인() {
        // given
        Notification notification = Notification.create(user, "알림 확인 테스트", resourceId, ResourceType.COMMENT);

        // when
        notification.confirm();

        // then
        assertThat(notification.isConfirmed()).isTrue();
        
    }

    @Test
    @DisplayName("알림을 확인하면 confirmedAt이 설정된다.")
    void 알림_확인시_confirmedAt_설정() {
        // given
        Notification notification = Notification.create(user, "알림 확인 테스트", resourceId, ResourceType.COMMENT);

        // when
        notification.confirm();

        // then
        assertThat(notification.getConfirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 확인된 알림을 다시 확인해도 confirmedAt은 최초 확인 시각을 유지한다.")
    void 이미_확인된_알림_재확인시_confirmedAt_유지() {
        // given
        Notification notification = Notification.create(user, "알림 확인 테스트", resourceId, ResourceType.COMMENT);
        notification.confirm();
        LocalDateTime firstConfirmedAt = notification.getConfirmedAt();

        // when
        notification.confirm();

        // then
        assertThat(notification.getConfirmedAt()).isEqualTo(firstConfirmedAt);
    }

    @Test
    @DisplayName("confirm(시각)을 호출하면 전달한 시각이 confirmedAt으로 설정된다")
    void 지정한_시각으로_확인_처리() {
        // given
        Notification notification = Notification.create(user, "알림", resourceId, ResourceType.COMMENT);
        LocalDateTime specificTime = LocalDateTime.of(2026, 1, 1, 0, 0);

        // when
        notification.confirm(specificTime);

        // then
        assertThat(notification.isConfirmed()).isTrue();
        assertThat(notification.getConfirmedAt()).isEqualTo(specificTime);
    }
}
