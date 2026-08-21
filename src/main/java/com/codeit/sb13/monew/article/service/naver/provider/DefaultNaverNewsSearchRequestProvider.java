package com.codeit.sb13.monew.article.service.naver.provider;

import com.codeit.sb13.monew.article.service.naver.dto.NaverNewsSearchRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DefaultNaverNewsSearchRequestProvider implements NaverNewsSearchRequestProvider {
    @Override
    public List<NaverNewsSearchRequest> getRequests() {
        return List.of();
    }
}
