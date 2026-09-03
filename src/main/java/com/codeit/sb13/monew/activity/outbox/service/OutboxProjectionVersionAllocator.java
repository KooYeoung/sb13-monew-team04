package com.codeit.sb13.monew.activity.outbox.service;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxProjectionClock;
import com.codeit.sb13.monew.activity.outbox.repository.OutboxProjectionClockRepository;
import com.codeit.sb13.monew.global.exception.outbox.OutboxProjectionVersionAllocationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 현재 트랜잭션 안에서 전역 projection 버전을 하나 발급한다. */
@Component
@RequiredArgsConstructor
public class OutboxProjectionVersionAllocator {

    private final OutboxProjectionClockRepository repository;

    public long allocate() {
        try {
            return repository.findByIdForUpdate(OutboxProjectionClock.SINGLETON_ID)
                    .orElseThrow(OutboxProjectionVersionAllocationException::new)
                    .nextVersion();
        } catch (OutboxProjectionVersionAllocationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new OutboxProjectionVersionAllocationException(e);
        }
    }
}
