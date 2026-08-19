package com.codeit.sb13.monew.interest.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubscribeTest {

    @Test
    @DisplayName("of()는 관심사와 사용자 id를 조합해 구독을 생성한다")
    void of() {
        // given
        Interest interest = Interest.create("스포츠");
        UUID userId = UUID.randomUUID();

        // when
        Subscribe subscribe = Subscribe.of(interest, userId);

        // then
        assertThat(subscribe.getInterest()).isEqualTo(interest);
        assertThat(subscribe.getUserId()).isEqualTo(userId);
    }
}
