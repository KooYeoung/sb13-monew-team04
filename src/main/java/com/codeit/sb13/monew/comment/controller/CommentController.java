package com.codeit.sb13.monew.comment.controller;


import com.codeit.sb13.monew.comment.controller.dto.CommentRegisterRequest;
import com.codeit.sb13.monew.comment.controller.dto.CommentSearchRequest;
import com.codeit.sb13.monew.comment.service.CommentOrderBy;
import com.codeit.sb13.monew.comment.service.CommentService;
import com.codeit.sb13.monew.comment.service.dto.CommentDto;
import com.codeit.sb13.monew.comment.service.dto.CommentSearchCommand;
import com.codeit.sb13.monew.global.MonewHttpHeaders;
import com.codeit.sb13.monew.global.dto.CursorPageResponseDto;
import com.codeit.sb13.monew.global.exception.comment.CommentSearchConditionInvalidException;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/comments")
public class CommentController implements CommentApi {

  private final CommentService commentService;

  @Override
  @PostMapping
  public ResponseEntity<CommentDto> createComment(@Valid @RequestBody CommentRegisterRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(commentService.create(request.toCommand()));
  }

  @Override
  @GetMapping
  public ResponseEntity<CursorPageResponseDto<CommentDto>> searchComments(
      @Valid @ModelAttribute CommentSearchRequest request,
      @RequestHeader(MonewHttpHeaders.REQUEST_USER_ID) UUID requestUserId
  ) {
    validateSearchRequest(request);

    CommentSearchCommand command = new CommentSearchCommand(
        request.articleId(),
        CommentOrderBy.from(request.orderBy()),
        request.direction(),
        request.cursor(),
        request.after(),
        request.idAfter(),
        request.limit(),
        requestUserId
    );
    return ResponseEntity.status(HttpStatus.OK).body(commentService.search(command));
  }

  private void validateSearchRequest(CommentSearchRequest request) {
    if (request.articleId() == null) {
      throw new CommentSearchConditionInvalidException("articleId는 필수입니다.");
    }
    if (!StringUtils.hasText(request.orderBy())) {
      throw new CommentSearchConditionInvalidException("orderBy는 필수입니다.");
    }
    if (request.direction() == null) {
      throw new CommentSearchConditionInvalidException("direction은 필수입니다.");
    }
    if (request.limit() < 1) {
      throw new CommentSearchConditionInvalidException("limit은 1 이상이어야 합니다: " + request.limit());
    }

    boolean hasCursor = StringUtils.hasText(request.cursor());
    boolean hasAfter = request.after() != null;
    boolean hasIdAfter = request.idAfter() != null;
    if (hasCursor != hasAfter || hasCursor != hasIdAfter) {
      throw new CommentSearchConditionInvalidException(
          "cursor, after, idAfter 모두 함께 전달해야 합니다.");
    }
  }
}
