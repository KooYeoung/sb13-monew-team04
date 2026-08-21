package com.codeit.sb13.monew.article.service.naver.client;

import com.codeit.sb13.monew.article.service.dto.CollectedArticle;
import com.codeit.sb13.monew.article.service.naver.dto.NaverNewsSearchRequest;

import java.util.List;

public interface NaverNewsClient {
    List<CollectedArticle> search(NaverNewsSearchRequest request);
}
