package com.codeit.sb13.monew.article.controller.dto;

import com.codeit.sb13.monew.article.s3.service.dto.ArticleRestoreCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

public record ArticleRestoreRequest(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        @Schema(type = "string", format = "date-time", example = "2026-08-10T00:00:00")
        @NotNull
        LocalDateTime from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        @Schema(type = "string", format = "date-time", example = "2026-08-27T23:59:59")
        @NotNull
        LocalDateTime to
) {

    @AssertFalse(message = "복구 시작일은 종료일보다 이후일 수 없습니다.")
    public boolean isRestoreDateRangeInvalid() {
        return from != null && to != null && from.isAfter(to);
    }

    public ArticleRestoreCommand toRestoreCommand() {
        return new ArticleRestoreCommand(from.toLocalDate(), to.toLocalDate());
    }
}
