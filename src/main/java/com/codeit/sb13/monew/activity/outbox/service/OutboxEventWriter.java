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

@Service
@RequiredArgsConstructor
public class OutboxEventWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxPayloadSerializer payloadSerializer;

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
