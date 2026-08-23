package com.codeit.sb13.monew.notification.service.dto;

import com.codeit.sb13.monew.global.exception.notification.NotificationInvalidCursorException;
import com.codeit.sb13.monew.global.exception.notification.NotificationInvalidLimitException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("NotificationFindDto")
class NotificationFindDtoTest {

    @Test
    @DisplayName("cursor/after가 둘 다 없으면 정상적으로 생성된다.")
    void cursor와_after가_둘다_없으면_정상생성() {
        // given
        UUID userId = UUID.randomUUID();

        // when
        NotificationFindDto dto = NotificationFindDto.of(null, null, 10, userId);

        // then
        assertThat(dto.cursorId()).isNull();
        assertThat(dto.after()).isNull();
        assertThat(dto.limit()).isEqualTo(10);
        assertThat(dto.userId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("cursor/after가 둘 다 있으면 cursor가 UUID로 파싱되어 생성된다.")
    void cursor와_after가_둘다_있으면_정상생성() {
        // given
        UUID cursorId = UUID.randomUUID();
        LocalDateTime after = LocalDateTime.now();
        UUID userId = UUID.randomUUID();

        // when
        NotificationFindDto dto = NotificationFindDto.of(cursorId.toString(), after, 10, userId);

        // then
        assertThat(dto.cursorId()).isEqualTo(cursorId);
        assertThat(dto.after()).isEqualTo(after);
    }

    @Test
    @DisplayName("cursor만 있고 after가 없으면 NotificationInvalidCursorException이 발생한다.")
    void cursor만_있으면_예외() {
        // given & when & then
        assertThatThrownBy(() -> NotificationFindDto.of(UUID.randomUUID().toString(), null, 10, UUID.randomUUID()))
                .isInstanceOf(NotificationInvalidCursorException.class);
    }

    @Test
    @DisplayName("after만 있고 cursor가 없으면 NotificationInvalidCursorException이 발생한다.")
    void after만_있으면_예외() {
        // given & when & then
        assertThatThrownBy(() -> NotificationFindDto.of(null, LocalDateTime.now(), 10, UUID.randomUUID()))
                .isInstanceOf(NotificationInvalidCursorException.class);
    }

    @Test
    @DisplayName("cursor가 UUID 형식이 아니면 NotificationInvalidCursorException이 발생한다.")
    void cursor_형식_오류면_예외() {
        // given & when & then
        assertThatThrownBy(() -> NotificationFindDto.of("not-a-uuid", LocalDateTime.now(), 10, UUID.randomUUID()))
                .isInstanceOf(NotificationInvalidCursorException.class);
    }

    @Test
    @DisplayName("limit이 0 이하면 NotificationInvalidLimitException이 발생한다.")
    void limit_0이하면_예외() {
        // given & when & then
        assertThatThrownBy(() -> NotificationFindDto.of(null, null, 0, UUID.randomUUID()))
                .isInstanceOf(NotificationInvalidLimitException.class);
    }
    @Test
    @DisplayName("limit이 MAX_LIMIT과 같으면 정상적으로 생성된다.")
    void limit이_MAX_LIMIT과_같으면_정상생성() {
        // given
        int maxLimit = (int) ReflectionTestUtils.getField(NotificationFindDto.class, "MAX_LIMIT");

        // when
        NotificationFindDto dto = NotificationFindDto.of(null, null, maxLimit, UUID.randomUUID());

        // then
        assertThat(dto.limit()).isEqualTo(maxLimit);
    }
    @Test
    @DisplayName("limit이 MAX_LIMIT을 초과하면 NotificationInvalidLimitException이 발생한다.")
    void limit이_MAX_LIMIT_초과면_예외() {
        // given & when & then
        assertThatThrownBy(() -> NotificationFindDto.of(null, null, Integer.MAX_VALUE, UUID.randomUUID()))
                .isInstanceOf(NotificationInvalidLimitException.class);
    }

    @Test
    @DisplayName("정규 생성자에 limit이 MAX_LIMIT을 초과하면 NotificationInvalidLimitException이 발생한다.")
    void 정규생성자_limit_초과_예외() {
        // given & when & then
        assertThatThrownBy(() -> new NotificationFindDto(null, null, Integer.MAX_VALUE, UUID.randomUUID()))
                .isInstanceOf(NotificationInvalidLimitException.class);
    }

    @Test
    @DisplayName("정규 생성자에 cursorId만 있고 after가 없으면 NotificationInvalidCursorException이 발생한다.")
    void 정규생성자_cursorId만_있으면_예외() {
        // given & when & then
        assertThatThrownBy(() -> new NotificationFindDto(UUID.randomUUID(), null, 10, UUID.randomUUID()))
                .isInstanceOf(NotificationInvalidCursorException.class);
    }

    @Test
    @DisplayName("정규 생성자에 after만 있고 cursorId가 없으면 NotificationInvalidCursorException이 발생한다.")
    void 정규생성자_after만_있으면_예외() {
        // given & when & then
        assertThatThrownBy(() -> new NotificationFindDto(null, LocalDateTime.now(), 10, UUID.randomUUID()))
                .isInstanceOf(NotificationInvalidCursorException.class);
    }
}