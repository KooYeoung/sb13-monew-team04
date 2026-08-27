package com.codeit.sb13.monew.interest.controller.dto;

import com.codeit.sb13.monew.interest.service.dto.InterestOrderBy;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 관심사 목록 조회 요청의 쿼리 파라미터를 담는 DTO.
 *
 * <p>{@code Monew-Request-User-ID} 헤더는 쿼리 파라미터가 아니라 별도의
 * {@code @RequestHeader}로 받으므로 이 DTO에는 포함하지 않는다.</p>
 *
 * <p>{@code orderBy}, {@code direction}은 참조 타입이라 해당 쿼리 파라미터가
 * 아예 없으면 {@code @ModelAttribute} 바인딩 단계에서 예외 없이 {@code null}로
 * 채워진다. 이전에 {@code @RequestParam}이 대신 해주던 필수 여부 검증을
 * 스프링이 더 이상 해주지 않으므로, 컨트롤러에서 두 값의 {@code null} 여부를
 * 직접 확인한다.</p>
 *
 * @param keyword 검색어(관심사 이름 또는 키워드에 포함). 없으면 전체 대상
 * @param orderBy 정렬 기준({@code name} 또는 {@code subscriberCount})
 * @param direction 정렬 방향({@code ASC} 또는 {@code DESC})
 * @param cursor 이전 페이지 마지막 항목의 id. 첫 페이지 조회 시 생략
 * @param after 이전 페이지 마지막 항목의 생성 시각(보조 커서). 첫 페이지 조회 시 생략
 * @param limit 조회할 최대 개수. 1 미만이면 400(INT_006)으로 응답한다
 */
public record InterestSearchRequest(
        String keyword,
        InterestOrderBy orderBy,
        Sort.Direction direction,
        UUID cursor,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime after,
        int limit
) {
}
