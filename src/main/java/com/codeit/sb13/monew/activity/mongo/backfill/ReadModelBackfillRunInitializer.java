package com.codeit.sb13.monew.activity.mongo.backfill;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 설정된 run-id의 checkpoint가 없으면 독립 트랜잭션으로 최초 row를 만든다. */
@Service
@RequiredArgsConstructor
public class ReadModelBackfillRunInitializer {

    private final ReadModelBackfillRunRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureExists(UUID runId, LocalDateTime now) {
        if (!repository.existsById(runId)) {
            repository.saveAndFlush(ReadModelBackfillRun.start(runId, now));
        }
    }
}
