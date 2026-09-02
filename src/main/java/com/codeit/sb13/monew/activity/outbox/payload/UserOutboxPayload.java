package com.codeit.sb13.monew.activity.outbox.payload;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;

public record UserOutboxPayload(OutboxEventAction action) implements OutboxEventPayload {
}
