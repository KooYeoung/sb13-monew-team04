package com.codeit.sb13.monew.activity.outbox.payload;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * 사용자 물리삭제 전에 수집한 MongoDB cleanup 후보를 전달하는 payload다.
 *
 * <p>각 목록은 입력 순서를 유지하면서 중복이 제거된 불변 복사본이다. 목록은 삭제
 * 트랜잭션이 수집한 후보이며 완전한 snapshot을 보장하지 않으므로, worker는 원본
 * 존재 여부 확인과 함께 사용하고 payload만으로 문서를 복원하지 않는다.</p>
 *
 * @param action 사용자 물리삭제 동작
 * @param authoredCommentIds 사용자가 작성한 댓글 식별자 목록
 * @param impactedArticleIds 댓글·조회 관계로 영향을 받은 기사 식별자 목록
 * @param likedCommentIds 사용자가 좋아요한 댓글 식별자 목록
 * @param viewedArticleIds 사용자가 조회한 기사 식별자 목록
 * @param subscribedInterestIds 사용자가 구독한 관심사 식별자 목록
 * @param impact 삭제 전에 수집한 activity natural key와 작성 댓글 snapshot 식별자
 */
public record UserHardDeleteOutboxPayload(
        OutboxEventAction action,
        List<UUID> authoredCommentIds,
        List<UUID> impactedArticleIds,
        List<UUID> likedCommentIds,
        List<UUID> viewedArticleIds,
        List<UUID> subscribedInterestIds,
        ProjectionImpact impact
) implements OutboxEventPayload {

    public UserHardDeleteOutboxPayload {
        authoredCommentIds = distinctCopy(authoredCommentIds);
        impactedArticleIds = distinctCopy(impactedArticleIds);
        likedCommentIds = distinctCopy(likedCommentIds);
        viewedArticleIds = distinctCopy(viewedArticleIds);
        subscribedInterestIds = distinctCopy(subscribedInterestIds);
        impact = impact == null ? ProjectionImpact.EMPTY : impact;
    }

    public UserHardDeleteOutboxPayload(
            OutboxEventAction action,
            List<UUID> authoredCommentIds,
            List<UUID> impactedArticleIds,
            List<UUID> likedCommentIds,
            List<UUID> viewedArticleIds,
            List<UUID> subscribedInterestIds
    ) {
        this(action, authoredCommentIds, impactedArticleIds, likedCommentIds,
                viewedArticleIds, subscribedInterestIds, ProjectionImpact.EMPTY);
    }

    private static List<UUID> distinctCopy(List<UUID> values) {
        return values == null ? List.of() : List.copyOf(new LinkedHashSet<>(values));
    }
}
