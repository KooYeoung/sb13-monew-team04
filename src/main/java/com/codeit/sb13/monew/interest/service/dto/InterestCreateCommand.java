package com.codeit.sb13.monew.interest.service.dto;

import java.util.List;

/**
 * 관심사 등록 요청을 서비스 계층에 전달하기 위한 커맨드.
 *
 * <p>컨트롤러가 {@code @Valid}로 형식 검증을 마친 {@code InterestCreateRequest}를
 * 이 커맨드로 변환해 서비스에 넘긴다. 값 자체에 대한 도메인 검증(공백 여부,
 * 길이 제한 등)은 이 커맨드가 아니라 {@link com.codeit.sb13.monew.interest.domain.Interest}와
 * {@link com.codeit.sb13.monew.interest.domain.Keyword}가 책임진다.</p>
 *
 * @param name 등록할 관심사 이름
 * @param keywords 등록할 키워드 목록
 */
public record InterestCreateCommand(
        String name,
        List<String> keywords
) {
}
