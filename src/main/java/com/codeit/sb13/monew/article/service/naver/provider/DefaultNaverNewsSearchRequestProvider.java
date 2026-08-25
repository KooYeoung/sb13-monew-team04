package com.codeit.sb13.monew.article.service.naver.provider;

import com.codeit.sb13.monew.article.service.naver.dto.NaverNewsSearchRequest;
import com.codeit.sb13.monew.interest.service.InterestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 현재 구독자가 있는 관심사들의 키워드를 검색어로 삼아 네이버 뉴스 검색 요청을 만든다.
 *
 * <p>RSS 출처들과 달리 네이버 뉴스 검색 API는 검색어(query) 없이는 호출할 수 없다.
 * 어떤 검색어로 검색할지는 이 클래스가 정하는데, 구독자가 없는 관심사의 키워드까지
 * 검색어로 쓰면 아무도 필요로 하지 않는 결과를 위해 API 호출 한도만 낭비하게 되므로,
 * {@link InterestService#findSubscribedKeywords}가 돌려주는, 현재 구독자가 있는
 * 관심사의 키워드만 검색어로 쓴다. 같은 키워드가 여러 관심사에 겹쳐 있어도 그 메서드가
 * 이미 중복을 제거해 돌려주므로, 여기서는 키워드 하나당 요청 하나만 만들면 된다.</p>
 */
@Component
@RequiredArgsConstructor
public class DefaultNaverNewsSearchRequestProvider implements NaverNewsSearchRequestProvider {

    private final InterestService interestService;

    @Override
    public List<NaverNewsSearchRequest> getRequests() {
        return interestService.findSubscribedKeywords().stream()
                .map(NaverNewsSearchRequest::new)
                .toList();
    }
}
