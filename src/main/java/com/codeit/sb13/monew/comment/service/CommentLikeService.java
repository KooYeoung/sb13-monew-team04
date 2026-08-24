package com.codeit.sb13.monew.comment.service;

import com.codeit.sb13.monew.comment.service.dto.CommentLikeDto;
import com.codeit.sb13.monew.comment.service.dto.CommentLikeRegisterCommand;

public interface CommentLikeService {

  CommentLikeDto likeComment(CommentLikeRegisterCommand command); // 댓글 좋아요 등록
  void unlikeComment(CommentLikeRegisterCommand command); // 댓글 좋아요 취소

}
