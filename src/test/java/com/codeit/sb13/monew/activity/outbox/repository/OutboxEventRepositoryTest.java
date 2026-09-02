package com.codeit.sb13.monew.activity.outbox.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxEvent;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventStatus;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventType;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxAggregateType;
import com.codeit.sb13.monew.global.config.JpaAuditingConfig;
import com.codeit.sb13.monew.global.config.QueryDslConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import({QueryDslConfig.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
class OutboxEventRepositoryTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("Outbox 이벤트를 PENDING 기본 상태와 JSON payload로 저장한다")
    void savePendingEvent() {
        UUID aggregateId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        LocalDateTime occurredAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        JsonNode payload = JsonNodeFactory.instance.objectNode()
                .put("articleId", UUID.randomUUID().toString())
                .put("action", "WRITTEN");

        OutboxEvent saved = outboxEventRepository.saveAndFlush(OutboxEvent.createPending(
                OutboxEventType.COMMENT_WRITTEN,
                OutboxAggregateType.COMMENT,
                aggregateId,
                actorUserId,
                payload,
                occurredAt
        ));
        em.clear();

        OutboxEvent found = outboxEventRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getEventType()).isEqualTo(OutboxEventType.COMMENT_WRITTEN);
        assertThat(found.getAggregateType()).isEqualTo(OutboxAggregateType.COMMENT);
        assertThat(found.getAggregateId()).isEqualTo(aggregateId);
        assertThat(found.getActorUserId()).isEqualTo(actorUserId);
        assertThat(found.getPayloadJson()).isEqualTo(payload);
        assertThat(found.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(found.getRetryCount()).isZero();
        assertThat(found.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(found.getNextRetryAt()).isNull();
        assertThat(found.getProcessedAt()).isNull();
        assertThat(found.getLastError()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("사용자 주체가 없는 Outbox 이벤트도 저장할 수 있다")
    void saveEventWithoutActor() {
        OutboxEvent saved = outboxEventRepository.saveAndFlush(createPendingEvent(null));
        em.clear();

        OutboxEvent found = outboxEventRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getActorUserId()).isNull();
    }

    @Test
    @DisplayName("처리 실패와 DEAD_LETTER 전환 시 retry와 오류 정보를 기록한다")
    void markFailedAndDeadLetter() {
        OutboxEvent event = outboxEventRepository.saveAndFlush(createPendingEvent(UUID.randomUUID()));
        LocalDateTime nextRetryAt = LocalDateTime.now().plusMinutes(1).truncatedTo(ChronoUnit.MICROS);

        event.markFailed("첫 번째 실패", nextRetryAt);
        outboxEventRepository.flush();
        em.clear();

        OutboxEvent failed = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(failed.getRetryCount()).isEqualTo(1);
        assertThat(failed.getNextRetryAt()).isEqualTo(nextRetryAt);
        assertThat(failed.getLastError()).isEqualTo("첫 번째 실패");
        assertThat(failed.getProcessedAt()).isNull();

        failed.markDeadLetter("재시도 한도 초과");
        outboxEventRepository.flush();
        em.clear();

        OutboxEvent deadLetter = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(deadLetter.getStatus()).isEqualTo(OutboxEventStatus.DEAD_LETTER);
        assertThat(deadLetter.getRetryCount()).isEqualTo(2);
        assertThat(deadLetter.getNextRetryAt()).isNull();
        assertThat(deadLetter.getLastError()).isEqualTo("재시도 한도 초과");
        assertThat(deadLetter.getProcessedAt()).isNull();
    }

    @Test
    @DisplayName("처리 성공 시 처리 시각을 기록하고 재시도 오류 정보를 정리한다")
    void markProcessed() {
        OutboxEvent event = outboxEventRepository.saveAndFlush(createPendingEvent(UUID.randomUUID()));
        event.markFailed("일시적 오류", LocalDateTime.now().plusMinutes(1));
        LocalDateTime processedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);

        event.markProcessed(processedAt);
        outboxEventRepository.flush();
        em.clear();

        OutboxEvent processed = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(processed.getStatus()).isEqualTo(OutboxEventStatus.PROCESSED);
        assertThat(processed.getRetryCount()).isEqualTo(1);
        assertThat(processed.getProcessedAt()).isEqualTo(processedAt);
        assertThat(processed.getNextRetryAt()).isNull();
        assertThat(processed.getLastError()).isNull();
    }

    private OutboxEvent createPendingEvent(UUID actorUserId) {
        return OutboxEvent.createPending(
                OutboxEventType.ARTICLE_VIEWED,
                OutboxAggregateType.ARTICLE,
                UUID.randomUUID(),
                actorUserId,
                JsonNodeFactory.instance.objectNode().put("viewed", true),
                LocalDateTime.now()
        );
    }
}
