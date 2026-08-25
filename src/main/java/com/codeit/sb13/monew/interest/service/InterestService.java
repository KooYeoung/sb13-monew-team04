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
     * 기사 수집 스케줄러가 새로 저장한 기사 목록을 이 메서드에 넘겨주는
     * 형태를 가정하며, 수집 스케줄러가 실제로 어떤 형태(반환값, 이벤트 등)로
     * 새 기사 목록을 넘겨줄지는 아직 확정되지 않았으므로 이 진입점은
     * 수집 스케줄러 쪽과 협의되는 대로 조정될 수 있다.</p>
     *
     * @param newArticles 새로 수집된 기사 목록. {@code null}이거나 비어 있으면
     *                    아무 일도 하지 않는다.
     */
    void notifyForNewArticles(List<Article> newArticles);
}
