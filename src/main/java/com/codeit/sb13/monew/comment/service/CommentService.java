package com.codeit.sb13.monew.comment.service;

import com.codeit.sb13.monew.comment.service.dto.CommentDto;
import com.codeit.sb13.monew.comment.service.dto.CommentRegisterCommand;
import jakarta.validation.Valid;

public interface CommentService {

  CommentDto create(@Valid CommentRegisterCommand command);
}
