package com.codeit.sb13.monew.article.s3.service.dto;

import java.time.LocalDate;

public record ArticleRestoreCommand(LocalDate from, LocalDate to) {
}
