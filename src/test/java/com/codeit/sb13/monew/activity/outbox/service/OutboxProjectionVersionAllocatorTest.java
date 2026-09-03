package com.codeit.sb13.monew.activity.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxProjectionClock;
import com.codeit.sb13.monew.activity.outbox.repository.OutboxProjectionClockRepository;
import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import com.codeit.sb13.monew.global.exception.outbox.OutboxProjectionVersionAllocationException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxProjectionVersionAllocatorTest {

    private final OutboxProjectionClockRepository repository =
            mock(OutboxProjectionClockRepository.class);
    private final OutboxProjectionVersionAllocator allocator =
            new OutboxProjectionVersionAllocator(repository);

    @Test
    @DisplayName("singleton clock row를 잠금 조회해 다음 projection version을 발급한다")
    void allocatesNextVersionFromLockedClock() {
        OutboxProjectionClock clock = mock(OutboxProjectionClock.class);
        given(repository.findByIdForUpdate(OutboxProjectionClock.SINGLETON_ID))
                .willReturn(Optional.of(clock));
        given(clock.nextVersion()).willReturn(42L);

        assertThat(allocator.allocate()).isEqualTo(42L);

        then(repository).should().findByIdForUpdate(OutboxProjectionClock.SINGLETON_ID);
    }

    @Test
    @DisplayName("singleton clock row가 없으면 OBX_009 예외를 던진다")
    void missingClockThrowsCustomException() {
        given(repository.findByIdForUpdate(OutboxProjectionClock.SINGLETON_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(allocator::allocate)
                .isInstanceOfSatisfying(
                        OutboxProjectionVersionAllocationException.class,
                        exception -> assertThat(exception.getApiErrorCode())
                                .isEqualTo(ApiErrorCode.OUTBOX_PROJECTION_VERSION_ALLOCATION_FAILED)
                );
    }

    @Test
    @DisplayName("clock 저장소 실패는 OBX_009 예외로 변환한다")
    void repositoryFailureThrowsCustomException() {
        given(repository.findByIdForUpdate(OutboxProjectionClock.SINGLETON_ID))
                .willThrow(new IllegalStateException("lock failed"));

        assertThatThrownBy(allocator::allocate)
                .isInstanceOf(OutboxProjectionVersionAllocationException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }
}
