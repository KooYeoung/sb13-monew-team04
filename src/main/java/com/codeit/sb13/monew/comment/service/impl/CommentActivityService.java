package com.codeit.sb13.monew.comment.service.impl;

import com.codeit.sb13.monew.comment.repository.CommentRepository;
import com.codeit.sb13.monew.comment.service.dto.RecentCommentActivityDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CommentActivityService {
    private final CommentRepository commentRepository;

    public List<RecentCommentActivityDto> getRecentCommentActivities(UUID userId) {
        return commentRepository.findRecentCommentActivities(userId)
                .stream()
                .map(RecentCommentActivityDto::from)
                .toList();
    }
}
