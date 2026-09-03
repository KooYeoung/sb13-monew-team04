package com.codeit.sb13.monew.activity.outbox.payload;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** 원본 삭제·변경 전에 수집한 MongoDB fan-out projection 식별자 묶음이다. */
public record ProjectionImpact(
        List<ActivityProjectionKeyPayload> activityKeys,
        List<UUID> commentSnapshotIds
) {

    public static final ProjectionImpact EMPTY = new ProjectionImpact(List.of(), List.of());

    public ProjectionImpact {
        activityKeys = distinctCopy(activityKeys);
        commentSnapshotIds = distinctCopy(commentSnapshotIds);
    }

    private static <T> List<T> distinctCopy(List<T> values) {
        return values == null ? List.of() : List.copyOf(new LinkedHashSet<>(values));
    }
}
