package com.codeit.sb13.monew.activity.mongo.backfill;

/** 초기 투영 run의 projection, 검증과 종결 상태다. */
public enum ReadModelBackfillStatus {
    PENDING,
    RUNNING,
    FAILED,
    VERIFYING,
    VERIFICATION_FAILED,
    COMPLETED
}
