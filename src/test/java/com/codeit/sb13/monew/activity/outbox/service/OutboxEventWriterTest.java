package com.codeit.sb13.monew.activity.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxAggregateType;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEvent;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventStatus;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventType;
import com.codeit.sb13.monew.activity.outbox.payload.CommentOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxEventWriterTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OutboxPayloadSerializer payloadSerializer;

    @InjectMocks
    private OutboxEventWriter outboxEventWriter;

    @Test
    @DisplayName("공통 envelope와 직렬화한 payload body로 PENDING 이벤트를 저장한다")
    void writePendingEvent() {
        UUID commentId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        CommentOutboxPayload payload = new CommentOutboxPayload(
                articleId,
                OutboxEventAction.WRITTEN
        );
        JsonNode json = JsonNodeFactory.instance.objectNode()
                .put("articleId", articleId.toString())
                .put("action", OutboxEventAction.WRITTEN.name());
        given(payloadSerializer.serialize(payload)).willReturn(json);

        outboxEventWriter.write(
                OutboxEventType.COMMENT_WRITTEN,
                OutboxAggregateType.COMMENT,
                commentId,
                actorUserId,
                payload
        );

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        then(outboxEventRepository).should().save(captor.capture());
        OutboxEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo(OutboxEventType.COMMENT_WRITTEN);
        assertThat(event.getAggregateType()).isEqualTo(OutboxAggregateType.COMMENT);
        assertThat(event.getAggregateId()).isEqualTo(commentId);
        assertThat(event.getActorUserId()).isEqualTo(actorUserId);
        assertThat(event.getPayloadJson()).isEqualTo(json);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getOccurredAt()).isNotNull();
    }
}
