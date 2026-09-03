package com.codeit.sb13.monew.activity.outbox.worker.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxAggregateType;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEvent;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventStatus;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventType;
import com.codeit.sb13.monew.activity.outbox.repository.OutboxEventRepository;
import com.codeit.sb13.monew.activity.outbox.worker.OutboxEventStateService;
import com.codeit.sb13.monew.global.exception.outbox.OutboxClaimOwnershipLostException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.node.JsonNodeFactory;

@Tag("migration")
@SpringBootTest(properties = {
        "spring.datasource.url=${MONEW_MIGRATION_DB_URL:jdbc:postgresql://localhost:5432/monew}",
        "spring.datasource.username=${MONEW_MIGRATION_DB_USERNAME:monew}",
        "spring.datasource.password=${MONEW_MIGRATION_DB_PASSWORD:change-me}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.docker.compose.enabled=false",
        "monew.mongodb.enabled=false"
})
class OutboxClaimServicePostgreSqlTest {

    @Autowired
    private OutboxClaimService claimService;

    @Autowired
    private OutboxEventStateService stateService;

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM outbox_events");
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("두 PostgreSQL consumer는 겹치지 않는 batch를 병렬 claim한다")
    void concurrentConsumersClaimDisjointBatches() throws Exception {
        List<OutboxEvent> events = repository.saveAllAndFlush(List.of(
                pendingEvent(), pendingEvent(), pendingEvent(), pendingEvent()
        ));
        Set<UUID> expectedIds = ids(events);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<OutboxClaimBatch> first = executor.submit(() -> claimAfterSignal(ready, start));
        Future<OutboxClaimBatch> second = executor.submit(() -> claimAfterSignal(ready, start));
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        OutboxClaimBatch firstBatch = first.get(10, TimeUnit.SECONDS);
        OutboxClaimBatch secondBatch = second.get(10, TimeUnit.SECONDS);
        Set<UUID> firstIds = ids(firstBatch.events());
        Set<UUID> secondIds = ids(secondBatch.events());

        assertThat(firstIds).hasSize(2);
        assertThat(secondIds).hasSize(2);
        assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
        assertThat(union(firstIds, secondIds)).isEqualTo(expectedIds);
        assertThat(firstBatch.claimId()).isNotEqualTo(secondBatch.claimId());
    }

    @Test
    @DisplayName("유효한 claim은 건너뛰고 만료된 claim은 새 worker가 회수한다")
    void expiredClaimIsRecovered() {
        OutboxEvent event = repository.saveAndFlush(pendingEvent());
        OutboxClaimBatch first = claimService.claim(1, Duration.ofMinutes(5));

        assertThat(claimService.claim(1, Duration.ofMinutes(5)).isEmpty()).isTrue();
        jdbcTemplate.update("""
                UPDATE outbox_events
                SET claim_until = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE claim_id = ?
                """, first.claimId());

        OutboxClaimBatch recovered = claimService.claim(1, Duration.ofMinutes(5));

        assertThat(recovered.claimId()).isNotEqualTo(first.claimId());
        assertThat(recovered.events()).extracting(OutboxEvent::getId)
                .containsExactly(event.getId());
    }

    @Test
    @DisplayName("이전 claim UUID는 회수된 이벤트의 완료 상태를 덮어쓸 수 없다")
    void staleClaimCannotCompleteRecoveredEvent() {
        OutboxEvent event = repository.saveAndFlush(pendingEvent());
        OutboxClaimBatch first = claimService.claim(1, Duration.ofMinutes(5));
        jdbcTemplate.update("""
                UPDATE outbox_events
                SET claim_until = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE claim_id = ?
                """, first.claimId());
        OutboxClaimBatch recovered = claimService.claim(1, Duration.ofMinutes(5));
        LocalDateTime processedAt = LocalDateTime.now();

        assertThatThrownBy(() -> stateService.markProcessed(
                event.getId(), first.claimId(), processedAt
        )).isInstanceOf(OutboxClaimOwnershipLostException.class);

        stateService.markProcessed(event.getId(), recovered.claimId(), processedAt);
        OutboxEvent processed = repository.findById(event.getId()).orElseThrow();
        assertThat(processed.getProcessedAt()).isNotNull();
        assertThat(processed.getClaimId()).isNull();
        assertThat(processed.getClaimUntil()).isNull();
    }

    @Test
    @DisplayName("실패 상태 전이는 retry를 기록하고 claim을 해제한다")
    void failureTransitionReleasesClaim() {
        OutboxEvent event = repository.saveAndFlush(pendingEvent());
        OutboxClaimBatch batch = claimService.claim(1, Duration.ofMinutes(5));
        LocalDateTime failedAt = LocalDateTime.now();

        stateService.markFailed(
                event.getId(),
                batch.claimId(),
                new IllegalStateException("mongo unavailable"),
                failedAt
        );

        OutboxEvent failed = repository.findById(event.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(failed.getRetryCount()).isEqualTo(1);
        assertThat(failed.getNextRetryAt()).isAfter(failedAt);
        assertThat(failed.getClaimId()).isNull();
        assertThat(failed.getClaimUntil()).isNull();
    }

    @Test
    @DisplayName("heartbeat 갱신은 현재 claim의 만료 시각을 연장한다")
    void renewExtendsClaimLease() {
        OutboxEvent event = repository.saveAndFlush(pendingEvent());
        OutboxClaimBatch batch = claimService.claim(1, Duration.ofMinutes(5));
        LocalDateTime previousClaimUntil = batch.events().get(0).getClaimUntil();

        int renewed = claimService.renew(batch.claimId(), Duration.ofMinutes(10));

        OutboxEvent found = repository.findById(event.getId()).orElseThrow();
        assertThat(renewed).isEqualTo(1);
        assertThat(found.getClaimUntil()).isAfter(previousClaimUntil);
    }

    private OutboxClaimBatch claimAfterSignal(
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("claim 동시 실행 신호를 기다리지 못했습니다.");
        }
        return claimService.claim(2, Duration.ofMinutes(5));
    }

    private OutboxEvent pendingEvent() {
        return OutboxEvent.createPending(
                OutboxEventType.ARTICLE_VIEWED,
                OutboxAggregateType.ARTICLE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                JsonNodeFactory.instance.objectNode().put("action", "VIEWED"),
                LocalDateTime.now(),
                1L
        );
    }

    private Set<UUID> ids(List<OutboxEvent> events) {
        Set<UUID> ids = new HashSet<>();
        for (OutboxEvent event : events) {
            ids.add(event.getId());
        }
        return ids;
    }

    private Set<UUID> union(Set<UUID> first, Set<UUID> second) {
        Set<UUID> result = new HashSet<>(first);
        result.addAll(second);
        return result;
    }
}
