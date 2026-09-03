package com.codeit.sb13.monew.activity.outbox.worker;

/**
 * 한 번의 polling 실행 결과를 나타낸다.
 *
 * @param selected 현재 실행이 claim한 이벤트 수
 * @param processed MongoDB 반영과 완료 상태 저장에 성공한 이벤트 수
 * @param failed 실패 또는 Dead Letter 상태 저장에 성공한 이벤트 수
 */
public record OutboxWorkerResult(int selected, int processed, int failed) {

    /**
     * claim할 이벤트가 없었던 실행 결과를 반환한다.
     *
     * @return 모든 집계가 {@code 0}인 결과
     */
    public static OutboxWorkerResult empty() {
        return new OutboxWorkerResult(0, 0, 0);
    }

    /**
     * lease 또는 상태 저장 문제로 이번 실행에서 종결하지 못한 이벤트 수를 계산한다.
     *
     * @return {@code selected - processed - failed}
     */
    public int unprocessed() {
        return selected - processed - failed;
    }
}
