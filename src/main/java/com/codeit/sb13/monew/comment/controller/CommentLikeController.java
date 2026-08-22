package com.codeit.sb13.monew.comment.controller;

import com.codeit.sb13.monew.comment.service.CommentLikeService;
import com.codeit.sb13.monew.comment.service.dto.CommentLikeDto;
import com.codeit.sb13.monew.comment.service.dto.CommentLikeRegisterCommand;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/comments")
public class CommentLikeController implements CommentLikeApi {

  private final CommentLikeService commentLikeService;

  @Override
  @PostMapping("/{commentId}/comment-likes")
  public ResponseEntity<CommentLikeDto> likeComment(@PathVariable UUID commentId, @RequestHeader("Monew-Request-User-ID") UUID requestUserId) {
    CommentLikeRegisterCommand command = new CommentLikeRegisterCommand(commentId, requestUserId);
    return ResponseEntity.status(HttpStatus.OK).body(commentLikeService.likeComment(command));
  }
}
