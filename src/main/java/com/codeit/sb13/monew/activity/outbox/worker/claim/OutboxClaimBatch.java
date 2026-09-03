package com.codeit.sb13.monew.activity.outbox.worker.claim;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxEvent;
import java.util.List;
import java.util.UUID;

/**
 * 한 worker 실행이 원자적으로 선점한 Outbox 이벤트 묶음이다.
 *
 * <p>같은 {@code claimId}가 모든 이벤트에 저장되며 목록은 외부에서 변경할 수
 * 없도록 복사된다.</p>
 *
 * @param claimId polling 실행을 식별하고 상태 갱신 소유권을 확인하는 UUID
 * @param events 현재 실행이 처리할 이벤트 목록
 */
public record OutboxClaimBatch(UUID claimId, List<OutboxEvent> events) {

    public OutboxClaimBatch {
        events = List.copyOf(events);
    }

    /**
     * 이번 polling에서 선점한 이벤트가 없는지 확인한다.
     *
     * @return 이벤트 목록이 비어 있으면 {@code true}
     */
    public boolean isEmpty() {
        return events.isEmpty();
    }
}
