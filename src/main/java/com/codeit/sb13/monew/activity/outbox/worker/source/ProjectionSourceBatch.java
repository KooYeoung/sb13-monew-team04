package com.codeit.sb13.monew.activity.outbox.worker.source;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 한 claim batch의 MongoDB projection에 필요한 RDB 현재 상태 모음이다.
 *
 * <p>모든 map과 내부 목록은 방어적으로 복사되며, map에 대상 ID가 없다는 것은
 * 처리 시점에 해당 source row가 존재하지 않는다는 뜻이다.</p>
 *
 * @param users 이벤트 actor와 사용자 aggregate의 현재 상태
 * @param interests 관심사와 keywords 및 구독자 수의 현재 상태
 * @param comments 댓글과 작성자·부모 기사 및 좋아요 수의 현재 상태
 * @param articles 기사 표시값과 조회·댓글 수의 현재 상태
 * @param subscriptions 관심사와 사용자별 현재 구독 관계
 * @param commentLikes 댓글과 사용자별 현재 좋아요 관계
 * @param articleViews 기사와 사용자별 현재 조회 관계
 */
public record ProjectionSourceBatch(
        Map<UUID, UserState> users,
        Map<UUID, InterestState> interests,
        Map<UUID, CommentState> comments,
        Map<UUID, ArticleState> articles,
        Map<RelationKey, RelationState> subscriptions,
        Map<RelationKey, RelationState> commentLikes,
        Map<RelationKey, RelationState> articleViews
) {

    public ProjectionSourceBatch {
        users = Map.copyOf(users);
        interests = Map.copyOf(interests);
        comments = Map.copyOf(comments);
        articles = Map.copyOf(articles);
        subscriptions = Map.copyOf(subscriptions);
        commentLikes = Map.copyOf(commentLikes);
        articleViews = Map.copyOf(articleViews);
    }

    /**
     * 사용자별 관계를 찾기 위한 복합 map key다.
     *
     * @param targetId 관심사·댓글·기사 같은 관계 대상 식별자
     * @param userId 관계를 소유한 사용자 식별자
     */
    public record RelationKey(UUID targetId, UUID userId) {
    }

    /**
     * 이벤트 actor의 존재 여부와 논리삭제 상태를 판단하기 위한 사용자 정보다.
     *
     * @param id 사용자 식별자
     * @param nickname 현재 닉네임
     * @param active 논리삭제되지 않은 사용자이면 {@code true}
     */
    public record UserState(UUID id, String nickname, boolean active) {
    }

    /**
     * 관심사 snapshot을 재구성하기 위한 현재 상태다.
     *
     * @param id 관심사 식별자
     * @param name 현재 관심사 이름
     * @param keywords 현재 keyword 목록
     * @param subscriberCount 현재 활성 구독자 수
     * @param updatedAt 원본 관심사의 마지막 수정 시각
     */
    public record InterestState(
            UUID id,
            String name,
            List<String> keywords,
            long subscriberCount,
            LocalDateTime updatedAt
    ) {
        public InterestState {
            keywords = List.copyOf(keywords);
        }
    }

    /**
     * 댓글 snapshot과 댓글 활동을 재구성하기 위한 현재 상태다.
     *
     * @param id 댓글 식별자
     * @param articleId 부모 기사 식별자
     * @param articleTitle 부모 기사의 현재 제목
     * @param authorUserId 댓글 작성자 식별자
     * @param authorNickname 작성자의 현재 닉네임
     * @param content 현재 댓글 내용
     * @param likeCount 현재 활성 좋아요 수
     * @param visible 댓글과 부모 기사가 모두 노출 가능하면 {@code true}
     * @param createdAt 댓글 작성 시각
     * @param updatedAt 댓글 마지막 수정 시각
     */
    public record CommentState(
            UUID id,
            UUID articleId,
            String articleTitle,
            UUID authorUserId,
            String authorNickname,
            String content,
            long likeCount,
            boolean visible,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    /**
     * 기사 snapshot과 기사 활동을 재구성하기 위한 현재 상태다.
     *
     * @param id 기사 식별자
     * @param title 현재 제목
     * @param summary 현재 요약
     * @param source 기사 출처
     * @param sourceUrl 원문 URL
     * @param publishedAt 게시 시각
     * @param viewCount 현재 조회 수
     * @param commentCount 현재 노출 댓글 수
     * @param visible 기사가 노출 가능하면 {@code true}
     * @param updatedAt 기사 마지막 수정 시각
     */
    public record ArticleState(
            UUID id,
            String title,
            String summary,
            ArticleSource source,
            String sourceUrl,
            LocalDateTime publishedAt,
            long viewCount,
            long commentCount,
            boolean visible,
            LocalDateTime updatedAt
    ) {
    }

    /**
     * 구독·좋아요·조회 activity의 현재 관계 상태다.
     *
     * @param id 관계 row 식별자이며 activity의 source 식별자로 사용된다
     * @param targetId 관계 대상 식별자
     * @param userId 관계 사용자 식별자
     * @param active 현재 노출 가능한 관계이면 {@code true}
     * @param occurredAt 관계가 생성되거나 마지막으로 갱신된 활동 시각
     */
    public record RelationState(
            UUID id,
            UUID targetId,
            UUID userId,
            boolean active,
            LocalDateTime occurredAt
    ) {
    }
}
