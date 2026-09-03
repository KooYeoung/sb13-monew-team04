package com.codeit.sb13.monew.activity.outbox.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxAggregateType;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEvent;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventStatus;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventType;
import com.codeit.sb13.monew.activity.outbox.repository.OutboxEventRepository;
import com.codeit.sb13.monew.global.config.JpaAuditingConfig;
import com.codeit.sb13.monew.global.config.QueryDslConfig;
import com.codeit.sb13.monew.global.exception.outbox.OutboxClaimOwnershipLostException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.node.JsonNodeFactory;

@DataJpaTest
@Import({
        QueryDslConfig.class,
        JpaAuditingConfig.class,
        OutboxEventStateService.class,
        OutboxRetryPolicy.class
})
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxEventStateServiceIntegrationTest {

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private OutboxEventStateService stateService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("count 그룹 일부의 claim이 다르면 완료 상태 변경 전체를 롤백한다")
    void rollsBackPartiallyOwnedCountGroup() {
        UUID claimId = UUID.randomUUID();
        UUID otherClaimId = UUID.randomUUID();
        List<UUID> eventIds = new TransactionTemplate(transactionManager).execute(status -> {
            OutboxEvent owned = repository.save(pendingEvent(1L));
            OutboxEvent lost = repository.save(pendingEvent(2L));
            ReflectionTestUtils.setField(owned, "claimId", claimId);
            ReflectionTestUtils.setField(lost, "claimId", otherClaimId);
            repository.flush();
            return List.of(owned.getId(), lost.getId());
        });

        assertThatThrownBy(() -> stateService.markProcessed(
                eventIds,
                claimId,
                LocalDateTime.of(2026, 9, 3, 10, 0)
        )).isInstanceOf(OutboxClaimOwnershipLostException.class);

        List<OutboxEvent> stored = repository.findAllById(eventIds);
        assertThat(stored).allSatisfy(event ->
                assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING));
        assertThat(stored).extracting(OutboxEvent::getClaimId)
                .containsExactlyInAnyOrder(claimId, otherClaimId);
    }

    private OutboxEvent pendingEvent(long projectionVersion) {
        return OutboxEvent.createPending(
                OutboxEventType.ARTICLE_VIEW_COUNT_CHANGED,
                OutboxAggregateType.ARTICLE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                JsonNodeFactory.instance.objectNode().put("action", "COUNT_CHANGED"),
                LocalDateTime.of(2026, 9, 3, 9, 0),
                projectionVersion
        );
    }
}
