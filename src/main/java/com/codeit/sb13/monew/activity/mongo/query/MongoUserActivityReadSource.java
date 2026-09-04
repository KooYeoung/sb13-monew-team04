package com.codeit.sb13.monew.activity.mongo.query;

import com.codeit.sb13.monew.activity.service.UserActivityReadSource;
import com.codeit.sb13.monew.activity.service.UserActivitySections;
import com.codeit.sb13.monew.activity.service.dto.RecentSubscribed;
import com.codeit.sb13.monew.global.exception.readmodel.ReadModelQueryProgressException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** MongoDB Read Model을 기존 사용자 활동내역 네 영역으로 조합한다. */
@Component
@RequiredArgsConstructor
public class MongoUserActivityReadSource implements UserActivityReadSource {

    private static final int RECENT_ACTIVITY_LIMIT = 10;
    private static final int SUBSCRIPTION_PAGE_SIZE = 10;

    private final MongoActivityQueryReader queryReader;

    @Override
    public UserActivitySections read(UUID userId) {
        ActivityReadRequest firstPageRequest = new ActivityReadRequest(
                userId,
                null,
                RECENT_ACTIVITY_LIMIT
        );
        return new UserActivitySections(
                readAllSubscriptions(userId),
                queryReader.readComments(firstPageRequest).content(),
                queryReader.readCommentLikes(firstPageRequest).content(),
                queryReader.readArticleViews(firstPageRequest).content()
        );
    }

    /**
     * 기존 RDB 계약과 동일하게 현재 구독 전체를 반환한다.
     *
     * <p>MongoDB에서는 한 번에 10개 activity만 스캔한다. snapshot 필터링으로 응답
     * content가 비어도 {@code hasNext}가 참이면 scan cursor를 이동해 마지막 page까지
     * 읽는다. 다음 cursor가 없거나 내림차순으로 진행하지 않으면 무한 반복을 막기 위해
     * 조회를 중단한다.</p>
     */
    private List<RecentSubscribed> readAllSubscriptions(UUID userId) {
        List<RecentSubscribed> subscriptions = new ArrayList<>();
        ActivityReadCursor cursor = null;

        while (true) {
            ActivityReadPage<RecentSubscribed> page = queryReader.readSubscriptions(
                    new ActivityReadRequest(userId, cursor, SUBSCRIPTION_PAGE_SIZE)
            );
            subscriptions.addAll(page.content());
            if (!page.hasNext()) {
                return List.copyOf(subscriptions);
            }

            ActivityReadCursor nextCursor = page.nextCursor();
            verifyCursorProgress(userId, cursor, nextCursor);
            cursor = nextCursor;
        }
    }

    private void verifyCursorProgress(
            UUID userId,
            ActivityReadCursor currentCursor,
            ActivityReadCursor nextCursor
    ) {
        if (nextCursor == null) {
            throw new ReadModelQueryProgressException(
                    userId, currentCursor, null, "NEXT_CURSOR_MISSING"
            );
        }
        if (currentCursor != null && !isOlder(nextCursor, currentCursor)) {
            throw new ReadModelQueryProgressException(
                    userId, currentCursor, nextCursor, "CURSOR_NOT_DESCENDING"
            );
        }
    }

    private boolean isOlder(ActivityReadCursor nextCursor, ActivityReadCursor currentCursor) {
        int occurredAtComparison = nextCursor.occurredAt().compareTo(currentCursor.occurredAt());
        return occurredAtComparison < 0
                || (occurredAtComparison == 0
                && nextCursor.activityId().compareTo(currentCursor.activityId()) < 0);
    }
}
