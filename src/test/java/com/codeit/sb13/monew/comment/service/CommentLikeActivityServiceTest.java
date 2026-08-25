package com.codeit.sb13.monew.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.codeit.sb13.monew.comment.repository.CommentLikeRepository;
import com.codeit.sb13.monew.comment.repository.dto.RecentCommentLikeActivityProjection;
import com.codeit.sb13.monew.comment.service.dto.RecentCommentLikeActivityDto;
import com.codeit.sb13.monew.comment.service.impl.CommentLikeActivityService;
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
class CommentLikeActivityServiceTest {

    @Mock
    private CommentLikeRepository commentLikeRepository;

    @InjectMocks
    private CommentLikeActivityService commentLikeActivityService;

    @Test
    @DisplayName("getRecentCommentLikes maps repository projections to DTOs")
    void getRecentCommentLikes_mapsProjectionToDto() {
        UUID userId = UUID.randomUUID();
        RecentCommentLikeActivityProjection projection = new RecentCommentLikeActivityProjection(
                UUID.randomUUID(),
                LocalDateTime.of(2026, 8, 25, 13, 0),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "article title",
                UUID.randomUUID(),
                "commenter",
                "liked comment",
                5L,
                LocalDateTime.of(2026, 8, 25, 12, 0)
        );
        given(commentLikeRepository.findRecentCommentLikeActivity(userId)).willReturn(List.of(projection));

        List<RecentCommentLikeActivityDto> result = commentLikeActivityService.getRecentCommentLikes(userId);

        assertThat(result).singleElement()
                .satisfies(dto -> {
                    assertThat(dto.id()).isEqualTo(projection.id());
                    assertThat(dto.createdAt()).isEqualTo(projection.createdAt());
                    assertThat(dto.commentId()).isEqualTo(projection.commentId());
                    assertThat(dto.articleId()).isEqualTo(projection.articleId());
                    assertThat(dto.articleTitle()).isEqualTo("article title");
                    assertThat(dto.commentUserId()).isEqualTo(projection.commentUserId());
                    assertThat(dto.commentUserNickname()).isEqualTo("commenter");
                    assertThat(dto.commentContent()).isEqualTo("liked comment");
                    assertThat(dto.commentLikeCount()).isEqualTo(5L);
                    assertThat(dto.commentCreatedAt()).isEqualTo(projection.commentCreatedAt());
                });
        then(commentLikeRepository).should().findRecentCommentLikeActivity(userId);
    }
}
