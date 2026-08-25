package com.codeit.sb13.monew.article.service.naver.provider;

import com.codeit.sb13.monew.article.service.naver.dto.NaverNewsSearchRequest;
import com.codeit.sb13.monew.interest.service.InterestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("기본 네이버 뉴스 검색 요청 생성기 단위 테스트")
@ExtendWith(MockitoExtension.class)
class DefaultNaverNewsSearchRequestProviderTest {

    @Mock
    private InterestService interestService;

    private DefaultNaverNewsSearchRequestProvider provider;

    @Test
    @DisplayName("구독 중인 관심사 키워드마다 검색 요청을 하나씩 만든다")
    void createsOneRequestPerSubscribedKeyword() {
        // given
        provider = new DefaultNaverNewsSearchRequestProvider(interestService);
        when(interestService.findSubscribedKeywords()).thenReturn(List.of("축구", "AI"));

        // when
        List<NaverNewsSearchRequest> requests = provider.getRequests();

        // then
        assertThat(requests)
                .extracting(NaverNewsSearchRequest::query)
                .containsExactlyInAnyOrder("축구", "AI");
    }

    @Test
    @DisplayName("구독 중인 키워드가 없으면 빈 요청 목록을 반환한다")
    void noSubscribedKeywords_returnsEmptyRequestList() {
        // given
        provider = new DefaultNaverNewsSearchRequestProvider(interestService);
        when(interestService.findSubscribedKeywords()).thenReturn(List.of());

        // when
        List<NaverNewsSearchRequest> requests = provider.getRequests();

        // then
        assertThat(requests).isEmpty();
    }
}
