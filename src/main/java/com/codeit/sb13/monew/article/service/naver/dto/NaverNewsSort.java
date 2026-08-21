package com.codeit.sb13.monew.article.service.naver.dto;

import lombok.Getter;

@Getter
public enum NaverNewsSort {
    SIM("정확도순", "sim"), DATE("날짜순", "date");

    private final String description;
    private final String value;

    NaverNewsSort(String description, String value) {
        this.description = description;
        this.value = value;
    }
}
