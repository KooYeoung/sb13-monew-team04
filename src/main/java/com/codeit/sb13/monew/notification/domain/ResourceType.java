package com.codeit.sb13.monew.notification.domain;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum ResourceType {
    INTEREST,
    COMMENT;

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
