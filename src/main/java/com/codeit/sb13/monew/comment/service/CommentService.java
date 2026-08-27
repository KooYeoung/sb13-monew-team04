package com.codeit.sb13.monew.comment.service;

import com.codeit.sb13.monew.comment.service.dto.CommentDto;
import com.codeit.sb13.monew.comment.service.dto.CursorPageResponseCommentDto;
import com.codeit.sb13.monew.comment.service.dto.CommentRegisterCommand;
import com.codeit.sb13.monew.comment.service.dto.CommentSearchCommand;
import com.codeit.sb13.monew.comment.service.dto.CommentUpdateCommand;
import jakarta.validation.Valid;
import java.util.UUID;

public interface CommentService {

  CommentDto create(@Valid CommentRegisterCommand command);

  CursorPageResponseCommentDto search(@Valid CommentSearchCommand command);

  CommentDto update(@Valid CommentUpdateCommand command);

  void softDelete(UUID commentId);

  void hardDelete(UUID commentId);
}
