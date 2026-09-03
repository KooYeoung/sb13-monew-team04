package com.codeit.sb13.monew.activity.outbox.service;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxProjectionClock;
import com.codeit.sb13.monew.activity.outbox.repository.OutboxProjectionClockRepository;
import com.codeit.sb13.monew.global.exception.outbox.OutboxProjectionVersionAllocationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 현재 트랜잭션 안에서 전역 projection 버전을 하나 발급한다. */
@Component
@RequiredArgsConstructor
public class OutboxProjectionVersionAllocator {

    private final OutboxProjectionClockRepository repository;

    /**
     * 호출한 원본 변경 트랜잭션에 참여해 다음 projection version을 발급한다.
     *
     * @return 현재 전역 버전보다 1 큰 projection version
     * @throws org.springframework.transaction.IllegalTransactionStateException 활성 트랜잭션 없이 호출한 경우
     * @throws OutboxProjectionVersionAllocationException clock row 조회·잠금 또는 버전 발급에 실패한 경우
     */
    @Transactional(propagation = Propagation.MANDATORY)
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
