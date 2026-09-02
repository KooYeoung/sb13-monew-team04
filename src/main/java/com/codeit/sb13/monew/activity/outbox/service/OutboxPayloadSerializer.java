package com.codeit.sb13.monew.activity.outbox.service;

import com.codeit.sb13.monew.activity.outbox.payload.OutboxEventPayload;
import com.codeit.sb13.monew.global.exception.outbox.OutboxPayloadSerializationException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class OutboxPayloadSerializer {

    private final ObjectMapper objectMapper;

    public OutboxPayloadSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode serialize(OutboxEventPayload payload) {
        try {
            return objectMapper.valueToTree(payload);
        } catch (JacksonException e) {
            throw new OutboxPayloadSerializationException(payload.getClass().getSimpleName(), e);
        }
    }
}
