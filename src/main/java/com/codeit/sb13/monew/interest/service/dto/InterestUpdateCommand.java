package com.codeit.sb13.monew.interest.service.dto;

import java.util.List;
import java.util.UUID;

/**
 * 관심사 키워드 수정 요청을 서비스 계층에 전달하기 위한 커맨드.
 *
 * <p>이름은 수정 대상이 아니며, 전달받은 키워드 목록으로 기존 키워드
 * 전체를 교체한다. 개별 키워드에 대한 도메인 검증(공백 여부, 길이 제한 등)은
 * 이 커맨드가 아니라 {@link com.codeit.sb13.monew.interest.domain.Keyword}가
 * 책임진다.</p>
 *
 * @param interestId 키워드를 수정할 관심사 id
 * @param keywords 교체할 새 키워드 목록
 */
public record InterestUpdateCommand(
        UUID interestId,
        List<String> keywords
) {
}
