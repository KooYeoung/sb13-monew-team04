package com.codeit.sb13.monew.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventType;
import com.codeit.sb13.monew.activity.outbox.repository.OutboxEventRepository;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class UserHardDeleteOutboxIntegrationTest {

    @Autowired
    private UserHardDeleteExecutor userHardDeleteExecutor;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    @DisplayName("사용자 row를 물리삭제한 뒤에도 영향 ID snapshot Outbox payload를 보존한다")
    void hardDeleteRetainsSnapshotPayload() {
        User user = userRepository.saveAndFlush(User.builder()
                .email(UUID.randomUUID() + "@outbox.test")
                .nickname("hard-delete")
                .password("password")
                .build());

        userHardDeleteExecutor.hardDeleteUser(user.getId());

        assertThat(userRepository.findById(user.getId())).isEmpty();
        var event = outboxEventRepository.findAll().stream()
                .filter(candidate -> candidate.getEventType() == OutboxEventType.USER_HARD_DELETED)
                .filter(candidate -> candidate.getAggregateId().equals(user.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(event.getPayloadJson().path("action").asText())
                .isEqualTo(OutboxEventAction.HARD_DELETED.name());
        assertThat(event.getPayloadJson().path("authoredCommentIds").isArray()).isTrue();
        assertThat(event.getPayloadJson().path("impactedArticleIds").isArray()).isTrue();
        assertThat(event.getPayloadJson().path("likedCommentIds").isArray()).isTrue();
        assertThat(event.getPayloadJson().path("viewedArticleIds").isArray()).isTrue();
        assertThat(event.getPayloadJson().path("subscribedInterestIds").isArray()).isTrue();
    }
}
