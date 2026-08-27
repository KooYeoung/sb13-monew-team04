package com.codeit.sb13.monew.comment.controller;


import com.codeit.sb13.monew.comment.controller.dto.CommentRegisterRequest;
import com.codeit.sb13.monew.comment.controller.dto.CommentSearchRequest;
import com.codeit.sb13.monew.comment.controller.dto.CommentUpdateRequest;
import com.codeit.sb13.monew.comment.service.CommentOrderBy;
import com.codeit.sb13.monew.comment.service.CommentService;
import com.codeit.sb13.monew.comment.service.dto.CursorPageResponseCommentDto;
import com.codeit.sb13.monew.comment.service.dto.CommentDto;
import com.codeit.sb13.monew.comment.service.dto.CommentSearchCommand;
import com.codeit.sb13.monew.global.MonewHttpHeaders;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
  public ResponseEntity<CursorPageResponseCommentDto> searchComments(
      @Valid @ModelAttribute CommentSearchRequest request,
      @RequestHeader(MonewHttpHeaders.REQUEST_USER_ID) UUID requestUserId
  ) {
    CommentSearchCommand command = new CommentSearchCommand(
        request.articleId(),
        CommentOrderBy.from(request.orderBy()),
        request.direction(),
        request.cursor(),
        request.after(),
        request.limit(),
        requestUserId
    );
    return ResponseEntity.status(HttpStatus.OK).body(commentService.search(command));
  }

  // 댓글 내용 수정
  @Override
  @PatchMapping("/{commentId}")
  public ResponseEntity<CommentDto> updateComment(
      @PathVariable UUID commentId,
      @RequestHeader(MonewHttpHeaders.REQUEST_USER_ID) UUID requestUserId,
      @Valid @RequestBody CommentUpdateRequest request
  ) {
    return ResponseEntity.status(HttpStatus.OK).body(commentService.update(request.toCommand(commentId, requestUserId)));
  }


  // 댓글 논리 삭제
  @Override
  @DeleteMapping("/{commentId}")
  public ResponseEntity<Void> softDeleteComment(@PathVariable UUID commentId) {
    commentService.softDelete(commentId);
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  // 댓글 물리 삭제
  @Override
  @DeleteMapping("/{commentId}/hard")
  public ResponseEntity<Void> hardDeleteComment(@PathVariable UUID commentId) {
    commentService.hardDelete(commentId);
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }
}
