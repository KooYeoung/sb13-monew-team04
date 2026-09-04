package com.codeit.sb13.monew.activity.mongo.backfill;

import java.util.List;
import java.util.UUID;

/** 초기 투영 stage에서 읽은 한 keyset page다. */
public record InitialProjectionPage(
        List<InitialProjectionEvent> events,
        UUID lastSourceRowId
) {
    public InitialProjectionPage {
        events = List.copyOf(events);
    }

    public boolean isEmpty() {
        return events.isEmpty();
    }
}
