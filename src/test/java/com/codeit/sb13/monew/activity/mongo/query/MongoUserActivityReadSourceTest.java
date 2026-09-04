package com.codeit.sb13.monew.activity.mongo.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.sb13.monew.activity.service.UserActivitySections;
import com.codeit.sb13.monew.activity.service.dto.RecentSubscribed;
import com.codeit.sb13.monew.global.exception.readmodel.ReadModelQueryProgressException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MongoUserActivityReadSourceTest {

    @Mock
    MongoActivityQueryReader queryReader;

    @Test
    void readsAllSubscriptionsAndFirstTenItemsForRecentActivitySections() {
        List<RecentSubscribed> subscriptions = IntStream.rangeClosed(1, 12)
                .mapToObj(this::subscription)
                .toList();
        ActivityReadCursor nextCursor = cursor(2, "e");
        when(queryReader.readSubscriptions(any()))
                .thenReturn(new ActivityReadPage<>(subscriptions.subList(0, 10), nextCursor, true))
                .thenReturn(new ActivityReadPage<>(subscriptions.subList(10, 12), null, false));
        when(queryReader.readComments(any())).thenReturn(emptyPage());
        when(queryReader.readCommentLikes(any())).thenReturn(emptyPage());
        when(queryReader.readArticleViews(any())).thenReturn(emptyPage());
        MongoUserActivityReadSource source = new MongoUserActivityReadSource(queryReader);
        UUID userId = UUID.randomUUID();

        UserActivitySections sections = source.read(userId);

        assertThat(sections.subscriptions()).containsExactlyElementsOf(subscriptions);
        assertThat(sections.comments()).isEmpty();
        assertThat(sections.commentLikes()).isEmpty();
        assertThat(sections.articleViews()).isEmpty();
        ArgumentCaptor<ActivityReadRequest> subscriptionRequests = ArgumentCaptor.forClass(
                ActivityReadRequest.class
        );
        verify(queryReader, times(2)).readSubscriptions(subscriptionRequests.capture());
        assertThat(subscriptionRequests.getAllValues())
                .extracting(ActivityReadRequest::cursor)
                .containsExactly(null, nextCursor);
        assertThat(subscriptionRequests.getAllValues())
                .allSatisfy(request -> {
                    assertThat(request.userId()).isEqualTo(userId);
                    assertThat(request.limit()).isEqualTo(10);
                });

        ArgumentCaptor<ActivityReadRequest> recentRequests = ArgumentCaptor.forClass(
                ActivityReadRequest.class
        );
        verify(queryReader).readComments(recentRequests.capture());
        verify(queryReader).readCommentLikes(recentRequests.capture());
        verify(queryReader).readArticleViews(recentRequests.capture());
        assertThat(recentRequests.getAllValues()).allSatisfy(request -> {
            assertThat(request.userId()).isEqualTo(userId);
            assertThat(request.cursor()).isNull();
            assertThat(request.limit()).isEqualTo(10);
        });
    }

    @Test
    void continuesSubscriptionScanWhenFilteredPageIsEmpty() {
        ActivityReadCursor firstCursor = cursor(2, "f");
        ActivityReadCursor secondCursor = cursor(2, "e");
        RecentSubscribed expected = subscription(1);
        when(queryReader.readSubscriptions(any()))
                .thenReturn(new ActivityReadPage<>(List.of(), firstCursor, true))
                .thenReturn(new ActivityReadPage<>(List.of(), secondCursor, true))
                .thenReturn(new ActivityReadPage<>(List.of(expected), null, false));
        when(queryReader.readComments(any())).thenReturn(emptyPage());
        when(queryReader.readCommentLikes(any())).thenReturn(emptyPage());
        when(queryReader.readArticleViews(any())).thenReturn(emptyPage());

        UserActivitySections sections = new MongoUserActivityReadSource(queryReader)
                .read(UUID.randomUUID());

        assertThat(sections.subscriptions()).containsExactly(expected);
    }

    @Test
    void rejectsMissingNextCursor() {
        when(queryReader.readSubscriptions(any()))
                .thenReturn(new ActivityReadPage<>(List.of(), null, true));

        assertThatThrownBy(() -> new MongoUserActivityReadSource(queryReader)
                .read(UUID.randomUUID()))
                .isInstanceOf(ReadModelQueryProgressException.class)
                .hasMessageContaining("서버 내부 오류");
    }

    @Test
    void rejectsSubscriptionCursorThatDoesNotDescend() {
        ActivityReadCursor cursor = cursor(2, "e");
        when(queryReader.readSubscriptions(any()))
                .thenReturn(new ActivityReadPage<>(List.of(), cursor, true))
                .thenReturn(new ActivityReadPage<>(List.of(), cursor, true));

        assertThatThrownBy(() -> new MongoUserActivityReadSource(queryReader)
                .read(UUID.randomUUID()))
                .isInstanceOf(ReadModelQueryProgressException.class)
                .hasMessageContaining("서버 내부 오류");
    }

    private <T> ActivityReadPage<T> emptyPage() {
        return new ActivityReadPage<>(List.of(), null, false);
    }

    private RecentSubscribed subscription(int index) {
        return new RecentSubscribed(
                UUID.randomUUID(),
                LocalDateTime.of(2026, 9, 4, 12, index),
                UUID.randomUUID(),
                "interest-" + index,
                List.of("keyword-" + index),
                (long) index
        );
    }

    private ActivityReadCursor cursor(int minute, String idCharacter) {
        return new ActivityReadCursor(
                LocalDateTime.of(2026, 9, 4, 12, minute),
                idCharacter.repeat(64)
        );
    }
}
