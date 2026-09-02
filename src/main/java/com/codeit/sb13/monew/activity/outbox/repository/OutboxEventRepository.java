package com.codeit.sb13.monew.activity.outbox.repository;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
}
