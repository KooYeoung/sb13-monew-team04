package com.codeit.sb13.monew.interest.service.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InterestOrderByTest {

    @Test
    @DisplayName("\"name\"은 NAME으로 변환된다")
    void from_name_returnsName() {
        assertThat(InterestOrderBy.from("name")).isEqualTo(InterestOrderBy.NAME);
    }

    @Test
    @DisplayName("\"subscriberCount\"는 SUBSCRIBER_COUNT로 변환된다")
    void from_subscriberCount_returnsSubscriberCount() {
        assertThat(InterestOrderBy.from("subscriberCount")).isEqualTo(InterestOrderBy.SUBSCRIBER_COUNT);
    }

    @Test
    @DisplayName("허용되지 않은 값이면 IllegalArgumentException을 던진다")
    void from_invalidValue_throwsException() {
        assertThatThrownBy(() -> InterestOrderBy.from("invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
