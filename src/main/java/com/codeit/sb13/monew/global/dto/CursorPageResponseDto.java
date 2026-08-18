package com.codeit.sb13.monew.global.dto;

import java.util.List;

public record CursorPageResponseDto<T>(
        List<T> content,
        String nextCursor,
        String nextAfter,
        Integer size,
        Long totalElements,
        Boolean hasNext
) {

}
