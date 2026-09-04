package com.codeit.sb13.monew.activity.mongo.query;

import java.util.List;

/** snapshot 필터링 결과와 원본 activity scan 진행 위치를 함께 반환한다. */
public record ActivityReadPage<T>(
        List<T> content,
        ActivityReadCursor nextCursor,
        boolean hasNext
) {

    public ActivityReadPage {
        content = List.copyOf(content);
    }
}
