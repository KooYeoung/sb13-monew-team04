package com.codeit.sb13.monew.article.service.rss.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.hibernate.validator.constraints.time.DurationMin;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "monew.news.rss")
public record RssNewsProperties(
        @NotNull
        @DurationMin(nanos = 1)
        Duration connectTimeout,

        @NotNull
        @DurationMin(nanos = 1)
        Duration readTimeout,

        @Valid
        @NotNull
        Source hankyung,

        @Valid
        @NotNull
        Source chosun,

        @Valid
        @NotNull
        Source yonhap
) {

    public record Source(
            @NotBlank
            String baseUrl
    ) {

    }
}
