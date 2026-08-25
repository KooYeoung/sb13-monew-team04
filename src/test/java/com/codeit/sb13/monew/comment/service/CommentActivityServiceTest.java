package com.codeit.sb13.monew.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.codeit.sb13.monew.comment.repository.CommentRepository;
import com.codeit.sb13.monew.comment.repository.dto.RecentCommentActivityProjection;
import com.codeit.sb13.monew.comment.service.dto.RecentCommentActivityDto;
import com.codeit.sb13.monew.comment.service.impl.CommentActivityService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommentActivityServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @InjectMocks
    private CommentActivityService commentActivityService;

    @Test
    @DisplayName("getRecentCommentActivities maps repository projections to DTOs")
    void getRecentCommentActivities_mapsProjectionToDto() {
        UUID userId = UUID.randomUUID();
        RecentCommentActivityProjection projection = new RecentCommentActivityProjection(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "article title",
                userId,
                "tester",
                "comment content",
                3L,
                LocalDateTime.of(2026, 8, 25, 13, 0)
        );
        given(commentRepository.findRecentCommentActivities(userId)).willReturn(List.of(projection));

        List<RecentCommentActivityDto> result = commentActivityService.getRecentCommentActivities(userId);

        assertThat(result).singleElement()
                .satisfies(dto -> {
                    assertThat(dto.id()).isEqualTo(projection.id());
                    assertThat(dto.articleId()).isEqualTo(projection.articleId());
                    assertThat(dto.articleTitle()).isEqualTo("article title");
                    assertThat(dto.userId()).isEqualTo(userId);
                    assertThat(dto.userNickname()).isEqualTo("tester");
                    assertThat(dto.content()).isEqualTo("comment content");
                    assertThat(dto.likeCount()).isEqualTo(3L);
                    assertThat(dto.createdAt()).isEqualTo(projection.createdAt());
                });
        then(commentRepository).should().findRecentCommentActivities(userId);
    }
}
