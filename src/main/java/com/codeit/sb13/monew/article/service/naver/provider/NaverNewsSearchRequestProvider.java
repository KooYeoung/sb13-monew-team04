package com.codeit.sb13.monew.article.service.naver.provider;

import com.codeit.sb13.monew.article.service.naver.dto.NaverNewsSearchRequest;

import java.util.List;

public interface NaverNewsSearchRequestProvider {
    List<NaverNewsSearchRequest> getRequests();
}
