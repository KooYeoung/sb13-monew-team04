package com.codeit.sb13.monew.global.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ActivityVisibilityStatusTest {

    @Test
    @DisplayName("활동 노출 상태는 ACTIVE와 삭제 원인 상태를 가진다")
    void values() {
        assertThat(ActivityVisibilityStatus.values()).containsExactly(
                ActivityVisibilityStatus.ACTIVE,
                ActivityVisibilityStatus.USER_DELETED,
                ActivityVisibilityStatus.COMMENT_DELETED,
                ActivityVisibilityStatus.ARTICLE_DELETED
        );
    }
}
