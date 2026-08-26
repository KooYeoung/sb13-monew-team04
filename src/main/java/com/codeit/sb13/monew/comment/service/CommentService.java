package com.codeit.sb13.monew.comment.service;

import com.codeit.sb13.monew.comment.service.dto.CommentDto;
import com.codeit.sb13.monew.comment.service.dto.CommentRegisterCommand;
import com.codeit.sb13.monew.comment.service.dto.CommentSearchCommand;
import com.codeit.sb13.monew.comment.service.dto.CommentUpdateCommand;
import com.codeit.sb13.monew.global.dto.CursorPageResponseDto;
import jakarta.validation.Valid;

public interface CommentService {

  CommentDto create(@Valid CommentRegisterCommand command);

  CursorPageResponseDto<CommentDto> search(@Valid CommentSearchCommand command);

  CommentDto update(@Valid CommentUpdateCommand command);
}
