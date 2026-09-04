package com.codeit.sb13.monew.activity.mongo.backfill;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReadModelBackfillRunRepository
        extends JpaRepository<ReadModelBackfillRun, UUID> {

    @Query(value = "SELECT CURRENT_TIMESTAMP", nativeQuery = true)
    LocalDateTime currentTime();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT run FROM ReadModelBackfillRun run WHERE run.runId = :runId")
    Optional<ReadModelBackfillRun> findByIdForUpdate(@Param("runId") UUID runId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ReadModelBackfillRun run
            SET run.claimUntil = :claimUntil,
                run.updatedAt = :now
            WHERE run.runId = :runId
              AND run.claimId = :claimId
              AND run.status IN (
                    com.codeit.sb13.monew.activity.mongo.backfill.ReadModelBackfillStatus.RUNNING,
                    com.codeit.sb13.monew.activity.mongo.backfill.ReadModelBackfillStatus.VERIFYING
              )
            """)
    int renew(
            @Param("runId") UUID runId,
            @Param("claimId") UUID claimId,
            @Param("claimUntil") LocalDateTime claimUntil,
            @Param("now") LocalDateTime now
    );
}
