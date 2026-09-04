package com.codeit.sb13.monew.activity.mongo.query;

import com.codeit.sb13.monew.global.exception.readmodel.ReadModelQueryConditionInvalidException;
import java.util.UUID;

/** MongoDB 활동내역 한 유형을 cursor 기반으로 조회하는 내부 요청이다. */
public record ActivityReadRequest(
        UUID userId,
        ActivityReadCursor cursor,
        int limit
) {

    public ActivityReadRequest {
        if (userId == null) {
            throw new ReadModelQueryConditionInvalidException(
                    "userId", null, "조회할 사용자 ID가 필요합니다."
            );
        }
        if (limit < 1 || limit == Integer.MAX_VALUE) {
            throw new ReadModelQueryConditionInvalidException(
                    "limit", limit, "limit은 1 이상이며 Integer.MAX_VALUE보다 작아야 합니다."
            );
        }
    }
}
