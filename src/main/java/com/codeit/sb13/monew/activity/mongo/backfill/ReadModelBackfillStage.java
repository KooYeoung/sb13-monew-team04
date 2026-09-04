package com.codeit.sb13.monew.activity.mongo.backfill;

import java.util.Optional;

/** 초기 투영에서 순차적으로 스캔하는 네 활동 원본이다. */
public enum ReadModelBackfillStage {
    SUBSCRIPTION,
    COMMENT_WRITTEN,
    COMMENT_LIKED,
    ARTICLE_VIEWED;

    public Optional<ReadModelBackfillStage> next() {
        int nextOrdinal = ordinal() + 1;
        return nextOrdinal < values().length
                ? Optional.of(values()[nextOrdinal])
                : Optional.empty();
    }
}
