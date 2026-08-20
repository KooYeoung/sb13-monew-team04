package com.codeit.sb13.monew.article.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleRequest {
    private String title;
    private String summary;
    private String link;
    private LocalDateTime date;
    private String source;
}