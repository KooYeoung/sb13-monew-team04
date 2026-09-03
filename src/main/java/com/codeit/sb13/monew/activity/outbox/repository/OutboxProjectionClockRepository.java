package com.codeit.sb13.monew.activity.outbox.repository;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxProjectionClock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxProjectionClockRepository
        extends JpaRepository<OutboxProjectionClock, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT clock FROM OutboxProjectionClock clock WHERE clock.id = :id")
    Optional<OutboxProjectionClock> findByIdForUpdate(@Param("id") short id);
}
