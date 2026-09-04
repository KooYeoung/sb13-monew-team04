package com.codeit.sb13.monew.activity.mongo.query;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeit.sb13.monew.global.exception.readmodel.ReadModelQueryConditionInvalidException;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActivityReadRequestTest {

    @Test
    void cursorRequiresOccurredAtAndCanonicalActivityId() {
        assertThatThrownBy(() -> new ActivityReadCursor(null, "a".repeat(64)))
                .isInstanceOf(ReadModelQueryConditionInvalidException.class);
        assertThatThrownBy(() -> new ActivityReadCursor(LocalDateTime.now(), "not-a-hash"))
                .isInstanceOf(ReadModelQueryConditionInvalidException.class);
        assertThatThrownBy(() -> new ActivityReadCursor(LocalDateTime.now(), "A".repeat(64)))
                .isInstanceOf(ReadModelQueryConditionInvalidException.class);
    }

    @Test
    void requestRequiresUserAndPositiveSafeLimit() {
        assertThatThrownBy(() -> new ActivityReadRequest(null, null, 10))
                .isInstanceOf(ReadModelQueryConditionInvalidException.class);
        assertThatThrownBy(() -> new ActivityReadRequest(UUID.randomUUID(), null, 0))
                .isInstanceOf(ReadModelQueryConditionInvalidException.class);
        assertThatThrownBy(() -> new ActivityReadRequest(
                UUID.randomUUID(), null, Integer.MAX_VALUE
        )).isInstanceOf(ReadModelQueryConditionInvalidException.class);
    }
}
