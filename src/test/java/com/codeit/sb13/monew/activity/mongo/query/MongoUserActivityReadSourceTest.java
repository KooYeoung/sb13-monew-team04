package com.codeit.sb13.monew.activity.mongo.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.sb13.monew.activity.service.UserActivitySections;
import java.util.List;
import java.util.UUID;
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
    void readsFirstTenItemsForEveryActivitySection() {
        when(queryReader.readSubscriptions(any())).thenReturn(emptyPage());
        when(queryReader.readComments(any())).thenReturn(emptyPage());
        when(queryReader.readCommentLikes(any())).thenReturn(emptyPage());
        when(queryReader.readArticleViews(any())).thenReturn(emptyPage());
        MongoUserActivityReadSource source = new MongoUserActivityReadSource(queryReader);
        UUID userId = UUID.randomUUID();

        UserActivitySections sections = source.read(userId);

        assertThat(sections.subscriptions()).isEmpty();
        assertThat(sections.comments()).isEmpty();
        assertThat(sections.commentLikes()).isEmpty();
        assertThat(sections.articleViews()).isEmpty();
        ArgumentCaptor<ActivityReadRequest> request = ArgumentCaptor.forClass(
                ActivityReadRequest.class
        );
        verify(queryReader).readSubscriptions(request.capture());
        assertThat(request.getValue().userId()).isEqualTo(userId);
        assertThat(request.getValue().cursor()).isNull();
        assertThat(request.getValue().limit()).isEqualTo(10);
    }

    private <T> ActivityReadPage<T> emptyPage() {
        return new ActivityReadPage<>(List.of(), null, false);
    }
}
