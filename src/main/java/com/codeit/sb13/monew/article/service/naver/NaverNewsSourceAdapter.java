package com.codeit.sb13.monew.article.service.naver;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.NewsSourceAdapter;
import com.codeit.sb13.monew.article.service.dto.CollectedArticle;
import com.codeit.sb13.monew.article.service.naver.client.NaverNewsClient;
import com.codeit.sb13.monew.article.service.naver.dto.NaverNewsSearchRequest;
import com.codeit.sb13.monew.article.service.naver.provider.NaverNewsSearchRequestProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
@RequiredArgsConstructor
public class NaverNewsSourceAdapter implements NewsSourceAdapter {
    private final NaverNewsSearchRequestProvider provider;
    private final NaverNewsClient client;

    @Override
    public ArticleSource source() {
        return ArticleSource.NAVER;
    }

    @Override
    public List<CollectedArticle> fetch() {
        List<NaverNewsSearchRequest> requests = provider.getRequests();

        if (requests.isEmpty()) {
            return List.of();
        }

        return requests.stream()
                .map(client::search)
                .flatMap(Collection::stream)
                .toList();
    }
}
