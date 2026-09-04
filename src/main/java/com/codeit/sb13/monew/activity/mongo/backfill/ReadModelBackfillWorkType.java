package com.codeit.sb13.monew.activity.mongo.backfill;

/** scheduler가 선점한 작업의 종류다. */
public enum ReadModelBackfillWorkType {
    PROJECT,
    VERIFY,
    NONE
}
