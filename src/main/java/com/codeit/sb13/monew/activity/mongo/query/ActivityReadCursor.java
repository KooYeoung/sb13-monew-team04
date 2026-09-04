package com.codeit.sb13.monew.activity.mongo.query;

import com.codeit.sb13.monew.global.exception.readmodel.ReadModelQueryConditionInvalidException;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/** MongoDB 활동내역의 {@code occurredAt DESC, _id DESC} keyset cursor다. */
public record ActivityReadCursor(
        LocalDateTime occurredAt,
        String activityId
) {

    private static final Pattern SHA_256_ID = Pattern.compile("[0-9a-f]{64}");

    public ActivityReadCursor {
        if (occurredAt == null) {
            throw new ReadModelQueryConditionInvalidException(
                    "cursor.occurredAt", null, "cursor occurredAt이 필요합니다."
            );
        }
        if (activityId == null || !SHA_256_ID.matcher(activityId).matches()) {
            throw new ReadModelQueryConditionInvalidException(
                    "cursor.activityId", activityId,
                    "cursor activityId는 64자리 소문자 SHA-256 값이어야 합니다."
            );
        }
    }
}
