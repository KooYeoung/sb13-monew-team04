package com.codeit.sb13.monew.activity.outbox.domain;

/**
 * Outbox 이벤트의 처리 생명주기 상태다.
 *
 * <p>claim 여부는 이 상태와 분리된 {@code claimId}, {@code claimedAt},
 * {@code claimUntil}로 관리한다. 따라서 처리 중인 이벤트도 완료 전까지는
 * {@link #PENDING} 또는 {@link #FAILED} 상태를 유지한다.</p>
 */
public enum OutboxEventStatus {
    /** 최초 처리 대기 상태. */
    PENDING,
    /** MongoDB projection과 상태 저장이 모두 완료된 종결 상태. */
    PROCESSED,
    /** 처리에 실패해 다음 재시도 시각을 기다리는 상태. */
    FAILED,
    /** 최대 재시도 횟수를 소진해 자동 처리에서 제외된 종결 상태. */
    DEAD_LETTER
}
