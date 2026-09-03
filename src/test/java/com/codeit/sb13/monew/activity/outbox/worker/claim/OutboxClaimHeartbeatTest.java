package com.codeit.sb13.monew.activity.outbox.worker.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.codeit.sb13.monew.activity.outbox.worker.config.OutboxWorkerProperties;
import com.codeit.sb13.monew.global.exception.outbox.OutboxClaimOwnershipLostException;
import com.codeit.sb13.monew.global.exception.outbox.OutboxHeartbeatRenewException;
import com.codeit.sb13.monew.global.exception.outbox.OutboxHeartbeatStartException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OutboxClaimHeartbeatTest {

    private final OutboxClaimService claimService = mock(OutboxClaimService.class);
    private final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
    private final ScheduledFuture<?> future = mock(ScheduledFuture.class);
    private final ArgumentCaptor<Runnable> heartbeatTask = ArgumentCaptor.forClass(Runnable.class);
    private final OutboxWorkerProperties properties = new OutboxWorkerProperties(
            true,
            1000,
            100,
            Duration.ofMinutes(5),
            Duration.ofMinutes(1)
    );
    private OutboxClaimHeartbeat heartbeat;

    @BeforeEach
    void setUp() {
        given(executor.scheduleWithFixedDelay(
                heartbeatTask.capture(),
                eq(Duration.ofMinutes(1).toMillis()),
                eq(Duration.ofMinutes(1).toMillis()),
                eq(TimeUnit.MILLISECONDS)
        )).willAnswer(invocation -> future);
        heartbeat = new OutboxClaimHeartbeat(claimService, properties, executor);
    }

    @Test
    @DisplayName("heartbeat는 claim lease를 주기적으로 연장하고 종료 시 예약을 취소한다")
    void renewsClaimLease() {
        UUID claimId = UUID.randomUUID();
        given(claimService.renew(claimId, Duration.ofMinutes(5))).willReturn(2);

        OutboxClaimLease lease = heartbeat.start(claimId);
        heartbeatTask.getValue().run();
        lease.verifyHealthy();
        lease.close();

        verify(claimService).renew(claimId, Duration.ofMinutes(5));
        verify(future).cancel(false);
    }

    @Test
    @DisplayName("heartbeat 갱신 실패는 worker가 확인할 수 있도록 lease에 기록한다")
    void exposesHeartbeatFailure() {
        UUID claimId = UUID.randomUUID();
        IllegalStateException failure = new IllegalStateException("database unavailable");
        given(claimService.renew(claimId, Duration.ofMinutes(5))).willThrow(failure);

        OutboxClaimLease lease = heartbeat.start(claimId);
        heartbeatTask.getValue().run();

        assertThatThrownBy(lease::verifyHealthy)
                .isInstanceOfSatisfying(OutboxHeartbeatRenewException.class, exception -> {
                    assertThat(exception.getCause()).isSameAs(failure);
                    assertThat(exception.getDetails()).containsEntry("claimId", claimId);
                });
    }

    @Test
    @DisplayName("heartbeat scheduler 등록 실패를 커스텀 예외로 변환한다")
    void wrapsHeartbeatStartFailure() {
        UUID claimId = UUID.randomUUID();
        RejectedExecutionException failure = new RejectedExecutionException("executor stopped");
        given(executor.scheduleWithFixedDelay(
                any(Runnable.class),
                eq(Duration.ofMinutes(1).toMillis()),
                eq(Duration.ofMinutes(1).toMillis()),
                eq(TimeUnit.MILLISECONDS)
        )).willThrow(failure);

        assertThatThrownBy(() -> heartbeat.start(claimId))
                .isInstanceOfSatisfying(OutboxHeartbeatStartException.class, exception -> {
                    assertThat(exception.getCause()).isSameAs(failure);
                    assertThat(exception.getDetails()).containsEntry("claimId", claimId);
                });
    }

    @Test
    @DisplayName("heartbeat 갱신 대상이 없으면 claim 소유권 상실을 기록한다")
    void exposesClaimOwnershipLoss() {
        UUID claimId = UUID.randomUUID();
        given(claimService.renew(claimId, Duration.ofMinutes(5))).willReturn(0);

        OutboxClaimLease lease = heartbeat.start(claimId);
        heartbeatTask.getValue().run();

        assertThatThrownBy(lease::verifyHealthy)
                .isInstanceOfSatisfying(
                        OutboxClaimOwnershipLostException.class,
                        exception -> assertThat(exception.getDetails())
                                .containsEntry("claimId", claimId)
                );
    }
}
