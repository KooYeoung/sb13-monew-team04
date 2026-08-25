package com.codeit.sb13.monew.comment.repository.dto;

import java.util.List;

public record CommentSearchResult(
    List<CommentSearchProjection> rows,
    boolean hasNext,
    long totalElements

) {

}
