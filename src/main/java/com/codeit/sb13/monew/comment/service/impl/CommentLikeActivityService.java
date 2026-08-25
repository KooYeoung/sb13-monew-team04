package com.codeit.sb13.monew.comment.service.impl;

import com.codeit.sb13.monew.comment.repository.CommentLikeRepository;
import com.codeit.sb13.monew.comment.service.dto.RecentCommentLikeActivityDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CommentLikeActivityService {
    private final CommentLikeRepository commentLikeRepository;

    public List<RecentCommentLikeActivityDto> getRecentCommentLikes(UUID userId) {
        return commentLikeRepository.findRecentCommentLikeActivity(userId)
                .stream()
                .map(RecentCommentLikeActivityDto::from)
                .toList();
    }
}
