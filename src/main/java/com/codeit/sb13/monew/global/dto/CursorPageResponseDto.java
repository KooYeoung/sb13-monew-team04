package com.codeit.sb13.monew.global.dto;

import java.util.List;

/**
 * 커서 기반 페이지네이션 응답을 담는 공용 DTO.
 *
 * @param content 이번 페이지의 목록
 * @param nextCursor 다음 페이지 조회 시 {@code cursor} 파라미터로 그대로 돌려보낼 값
 * @param nextAfter 다음 페이지 조회 시 {@code after} 파라미터로 그대로 돌려보낼 값(보조 커서)
 * @param nextIdAfter 다음 페이지 조회 시 {@code idAfter} 파라미터로 그대로 돌려보낼 값(3차 커서,
 *                    타이브레이커). {@code nextCursor}와 {@code nextAfter}가 같은 항목이 여러 건
 *                    있을 때 순서를 확정하는 데 쓰인다
 * @param size 이번 페이지에 담긴 항목 수
 * @param totalElements 조건을 만족하는 전체 항목 수
 * @param hasNext 다음 페이지 존재 여부
 */
public record CursorPageResponseDto<T>(
        List<T> content,
        String nextCursor,
        String nextAfter,
        String nextIdAfter,
        Integer size,
        Long totalElements,
        Boolean hasNext
) {

}
