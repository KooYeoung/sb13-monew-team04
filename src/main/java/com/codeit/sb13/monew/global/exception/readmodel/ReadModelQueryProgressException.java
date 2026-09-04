package com.codeit.sb13.monew.global.exception.readmodel;

import com.codeit.sb13.monew.activity.mongo.query.ActivityReadCursor;
import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import java.util.Map;
import java.util.UUID;

/** MongoDB 활동 목록의 내부 page cursor가 다음 범위로 진행하지 않을 때 발생한다. */
public class ReadModelQueryProgressException extends ReadModelQueryException {

    public ReadModelQueryProgressException(
            UUID userId,
            ActivityReadCursor currentCursor,
            ActivityReadCursor nextCursor,
            String reason
    ) {
        super(ApiErrorCode.INTERNAL_SERVER_ERROR, Map.of(
                "userId", String.valueOf(userId),
                "currentCursor", String.valueOf(currentCursor),
                "nextCursor", String.valueOf(nextCursor),
                "reason", reason
        ));
    }
}
