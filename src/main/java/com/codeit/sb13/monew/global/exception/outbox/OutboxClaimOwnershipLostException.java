package com.codeit.sb13.monew.global.exception.outbox;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 상태를 갱신하려는 실행의 claim UUID가 현재 Outbox row와 일치하지 않을 때 발생한다.
 *
 * <p>lease가 만료돼 다른 worker가 이벤트를 회수했거나 이벤트가 이미 종결됐음을
 * 의미하므로, 기존 실행은 해당 batch의 추가 처리를 중단해야 한다.</p>
 */
public class OutboxClaimOwnershipLostException extends OutboxException {

    /**
     * 소유권을 잃은 이벤트 또는 batch 정보를 담아 예외를 생성한다.
     *
     * @param eventId 이벤트 식별자, heartbeat처럼 batch 단위 검사이면 {@code null}
     * @param claimId 소유권을 주장한 실행 UUID
     */
    public OutboxClaimOwnershipLostException(UUID eventId, UUID claimId) {
        super(ApiErrorCode.OUTBOX_CLAIM_OWNERSHIP_LOST, details(eventId, claimId));
    }

    private static Map<String, Object> details(UUID eventId, UUID claimId) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (eventId != null) {
            details.put("eventId", eventId);
        }
        details.put("claimId", claimId);
        return details;
    }
}
