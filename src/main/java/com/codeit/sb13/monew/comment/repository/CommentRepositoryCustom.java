package com.codeit.sb13.monew.comment.repository;

import com.codeit.sb13.monew.comment.repository.dto.CommentSearchCondition;
import com.codeit.sb13.monew.comment.repository.dto.CommentSearchResult;

public interface CommentRepositoryCustom {

  CommentSearchResult search(CommentSearchCondition condition);

}
