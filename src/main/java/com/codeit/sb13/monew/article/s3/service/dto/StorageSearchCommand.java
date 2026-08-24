package com.codeit.sb13.monew.article.s3.service.dto;

import java.time.LocalDate;

public record StorageSearchCommand(LocalDate backupDate) {
    public StorageSearchCommand {
        if (backupDate == null) {
            throw new IllegalArgumentException("Storage backupDate must not be null");
        }
    }
}
