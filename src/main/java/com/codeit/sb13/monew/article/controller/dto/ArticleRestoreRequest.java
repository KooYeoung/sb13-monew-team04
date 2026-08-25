package com.codeit.sb13.monew.article.controller.dto;

import com.codeit.sb13.monew.article.s3.service.dto.ArticleRestoreCommand;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record ArticleRestoreRequest(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @NotNull
        LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @NotNull
        LocalDate to
) {

    @AssertFalse(message = "복구 시작일은 종료일보다 이후일 수 없습니다.")
    public boolean isRestoreDateRangeInvalid() {
        return from != null && to != null && from.isAfter(to);
    }

    public ArticleRestoreCommand toRestoreCommand() {
        return new ArticleRestoreCommand(from, to);
    }
}
