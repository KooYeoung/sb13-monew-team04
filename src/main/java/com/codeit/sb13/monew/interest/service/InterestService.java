package com.codeit.sb13.monew.interest.service;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.global.dto.CursorPageResponseDto;
import com.codeit.sb13.monew.interest.controller.dto.InterestResponse;
import com.codeit.sb13.monew.interest.service.dto.InterestCreateCommand;
import com.codeit.sb13.monew.interest.service.dto.InterestSearchCommand;
import com.codeit.sb13.monew.interest.service.dto.InterestUpdateCommand;
import java.util.List;
import java.util.UUID;

public interface InterestService {

    /**
     * 새로운 관심사를 등록한다.
     *
     * @param command 등록할 관심사의 이름과 키워드 목록
     * @return 등록된 관심사 정보. 구독자 수는 0, 구독 여부는 false로 채워진다.
     * @throws com.codeit.sb13.monew.global.exception.interest.InterestNameDuplicatedException
     *         이미 존재하는 이름으로 등록을 시도한 경우
     */
    InterestResponse create(InterestCreateCommand command);

    /**
     * 관심사의 키워드를 수정한다.
     *
     * <p>이름은 수정 대상이 아니며, 전달받은 키워드 목록으로 기존 키워드
     * 전체를 교체한다. 이 요청에는 요청자 정보가 없어, 구독 여부는 항상
     * false로 응답한다.</p>
     *
     * @param command 수정할 관심사 id와 교체할 키워드 목록을 담은 커맨드
     * @return 수정된 관심사 정보
     * @throws com.codeit.sb13.monew.global.exception.interest.InterestNotFoundException
     *         해당 id의 관심사가 존재하지 않는 경우
     * @throws com.codeit.sb13.monew.global.exception.interest.InterestKeywordRequiredException
     *         교체할 키워드 목록이 비어 있는 경우
     */
    InterestResponse update(InterestUpdateCommand command);

    /**
     * 조건에 맞는 관심사 목록을 커서 기반으로 조회한다.
     *
     * @param command 검색어, 정렬 기준, 커서, 페이지 크기, 요청자 id를 담은 커맨드
     * @return 조회된 관심사 목록과 다음 페이지를 위한 커서 정보
     */
    CursorPageResponseDto<InterestResponse> search(InterestSearchCommand command);

    /**
     * 관심사를 물리적으로 삭제한다.
     *
     * <p>관심사에 속한 키워드와, 이 관심사를 구독 중인 사용자들의 구독 정보도 함께 삭제된다.
     * 삭제된 데이터는 복구할 수 없다.</p>
     *
     * @param interestId 삭제할 관심사 id
     * @throws com.codeit.sb13.monew.global.exception.interest.InterestNotFoundException
     *         해당 id의 관심사가 존재하지 않는 경우
     */
    void delete(UUID interestId);

    /**
     * 새로 수집된 기사 목록을 관심사 키워드와 비교해, 매칭되는 기사가 있는
     * 관심사의 구독자에게 알림을 보낸다.
     *
     * <p>관심사의 키워드 중 하나라도 기사의 제목 또는 요약에 포함되면
     * (대소문자 구분 없이) 그 기사는 해당 관심사와 매칭된 것으로 본다.
     * 매칭되는 기사가 하나도 없는 관심사는 건너뛴다. 매칭되는 기사가
     * 있어도 그 관심사를 구독 중인 (논리 삭제되지 않은) 사용자가 한 명도
     * 없으면 알림을 만들지 않는다.</p>
     *
     * <p>기사 수집 자체(외부 출처 호출, 저장)는 이 메서드의 책임이 아니다.
     * {@code ArticleCollectJobService}가 배치 하나에서 새로 저장한 기사 목록을
     * 모아 이 메서드를 호출하는 형태로 연동되어 있다(MID4-177).</p>
     *
     * @param newArticles 새로 수집된 기사 목록. {@code null}이거나 비어 있으면
     *                    아무 일도 하지 않는다.
     */
    void notifyForNewArticles(List<Article> newArticles);

    /**
     * 현재 구독자가 있는 관심사들의 키워드를 중복 없이 모아 반환한다.
     *
     * <p>네이버 뉴스 검색처럼 검색어(query) 없이는 호출할 수 없는 기사 수집 출처에서,
     * 어떤 키워드로 검색할지 정하는 데 쓰인다. 구독자가 한 명도 없는 관심사의 키워드까지
     * 검색어로 쓰면 아무도 필요로 하지 않는 결과를 위해 외부 API 호출 한도만 소모하게
     * 되므로, 논리 삭제되지 않은 사용자가 최소 한 명이라도 구독 중인 관심사의 키워드만
     * 대상으로 한다. 같은 키워드를 여러 관심사가 함께 갖고 있어도 검색 요청이 키워드당
     * 한 번만 만들어지도록 중복은 제거한다.</p>
     *
     * @return 구독 중인 관심사들의 키워드 목록 (중복 제거, 순서는 보장하지 않는다)
     */
    List<String> findSubscribedKeywords();
}
