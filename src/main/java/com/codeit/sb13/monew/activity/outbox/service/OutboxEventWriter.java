package com.codeit.sb13.monew.activity.outbox.service;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxAggregateType;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEvent;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventType;
import com.codeit.sb13.monew.activity.outbox.payload.OutboxEventPayload;
import com.codeit.sb13.monew.activity.outbox.repository.OutboxEventRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 도메인 변경 트랜잭션에 Outbox 이벤트를 함께 저장하는 진입점이다.
 *
 * <p>별도 트랜잭션을 열거나 비동기로 실행하지 않는다. 호출한 서비스의 트랜잭션에
 * 필수로 참여하므로 원본 변경과 Outbox row는 함께 커밋되거나 함께 롤백된다.</p>
 */
@Service
@RequiredArgsConstructor
public class OutboxEventWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxPayloadSerializer payloadSerializer;

    /**
     * 타입이 지정된 payload를 JSON으로 직렬화해 처리 대기 이벤트로 저장한다.
     *
     * @param eventType worker 처리 방식을 결정하는 이벤트 종류
     * @param aggregateType 원본 도메인 종류
     * @param aggregateId 원본 엔티티 식별자
     * @param actorUserId 이벤트를 발생시킨 사용자 식별자, 시스템 이벤트이면 {@code null}
     * @param payload 이벤트별 추가 식별자와 동작을 담은 payload
     * @return 현재 트랜잭션에 저장된 처리 대기 이벤트
     * @throws org.springframework.transaction.IllegalTransactionStateException 활성 트랜잭션 없이 호출한 경우
     * @throws com.codeit.sb13.monew.global.exception.outbox.OutboxPayloadSerializationException payload를 JSON으로 변환할 수 없는 경우
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent write(
            OutboxEventType eventType,
            OutboxAggregateType aggregateType,
            UUID aggregateId,
            UUID actorUserId,
            OutboxEventPayload payload
    ) {
        return outboxEventRepository.save(OutboxEvent.createPending(
                eventType,
                aggregateType,
                aggregateId,
                actorUserId,
                payloadSerializer.serialize(payload),
                LocalDateTime.now()
        ));
    }
}
