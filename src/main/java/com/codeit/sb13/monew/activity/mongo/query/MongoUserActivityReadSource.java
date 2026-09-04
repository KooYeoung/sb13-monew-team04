package com.codeit.sb13.monew.activity.mongo.query;

import com.codeit.sb13.monew.activity.service.UserActivityReadSource;
import com.codeit.sb13.monew.activity.service.UserActivitySections;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** MongoDB Read Model의 첫 page를 기존 사용자 활동내역 네 영역으로 조합한다. */
@Component
@RequiredArgsConstructor
public class MongoUserActivityReadSource implements UserActivityReadSource {

    private static final int RECENT_ACTIVITY_LIMIT = 10;

    private final MongoActivityQueryReader queryReader;

    @Override
    public UserActivitySections read(UUID userId) {
        ActivityReadRequest request = new ActivityReadRequest(
                userId,
                null,
                RECENT_ACTIVITY_LIMIT
        );
        return new UserActivitySections(
                queryReader.readSubscriptions(request).content(),
                queryReader.readComments(request).content(),
                queryReader.readCommentLikes(request).content(),
                queryReader.readArticleViews(request).content()
        );
    }
}
