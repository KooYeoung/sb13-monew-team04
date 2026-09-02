package com.codeit.sb13.monew.activity.outbox.service;

import com.codeit.sb13.monew.activity.outbox.payload.OutboxEventPayload;
import com.codeit.sb13.monew.global.exception.outbox.OutboxPayloadSerializationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class OutboxPayloadSerializer {

    private final ObjectMapper objectMapper;

    public OutboxPayloadSerializer() {
        this(new ObjectMapper());
    }

    OutboxPayloadSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode serialize(OutboxEventPayload payload) {
        try {
            return objectMapper.valueToTree(payload);
        } catch (IllegalArgumentException e) {
            throw new OutboxPayloadSerializationException(payload.getClass().getSimpleName(), e);
        }
    }
}
