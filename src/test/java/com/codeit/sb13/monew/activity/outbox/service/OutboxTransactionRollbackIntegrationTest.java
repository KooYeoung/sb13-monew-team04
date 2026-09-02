package com.codeit.sb13.monew.activity.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;

import com.codeit.sb13.monew.Sb13MonewTeam04Application;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxAggregateType;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventType;
import com.codeit.sb13.monew.activity.outbox.payload.UserOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.repository.OutboxEventRepository;
import com.codeit.sb13.monew.global.exception.outbox.OutboxPayloadSerializationException;
import com.codeit.sb13.monew.interest.domain.Interest;
import com.codeit.sb13.monew.interest.domain.Subscribe;
import com.codeit.sb13.monew.interest.repository.InterestRepository;
import com.codeit.sb13.monew.interest.repository.SubscribeRepository;
import com.codeit.sb13.monew.interest.service.SubscribeSaver;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = {
        Sb13MonewTeam04Application.class,
        OutboxTransactionRollbackIntegrationTest.RollbackProbeConfiguration.class
})
@ActiveProfiles("test")
class OutboxTransactionRollbackIntegrationTest {

    @Autowired
    private RollbackProbeService rollbackProbeService;

    @Autowired
    private SubscribeSaver subscribeSaver;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InterestRepository interestRepository;

    @Autowired
    private SubscribeRepository subscribeRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxEventWriter outboxEventWriter;

    @MockitoBean
    private OutboxPayloadSerializer payloadSerializer;

    @BeforeEach
    void setUp() {
        reset(payloadSerializer);
        outboxEventRepository.deleteAll();
        subscribeRepository.deleteAll();
        interestRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("일반 트랜잭션에서 Outbox 직렬화 실패 시 원본 변경도 롤백한다")
    void ordinaryTransactionRollsBackSourceWrite() {
        String email = UUID.randomUUID() + "@test.com";
        failSerialization();

        assertThatThrownBy(() -> rollbackProbeService.saveUserAndOutbox(email))
                .isInstanceOf(OutboxPayloadSerializationException.class);

        assertThat(userRepository.existsByEmail(email)).isFalse();
        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("REQUIRES_NEW 트랜잭션에서 Outbox 직렬화 실패 시 원본 변경도 롤백한다")
    void requiresNewTransactionRollsBackSourceWrite() {
        Interest interest = Interest.create("관심사-" + UUID.randomUUID());
        interest.addKeyword("키워드");
        interestRepository.saveAndFlush(interest);
        UUID userId = UUID.randomUUID();
        failSerialization();

        assertThatThrownBy(() -> subscribeSaver.save(Subscribe.of(interest, userId)))
                .isInstanceOf(OutboxPayloadSerializationException.class);

        assertThat(subscribeRepository.findByInterest_IdAndUserId(interest.getId(), userId))
                .isEmpty();
        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("Outbox writer는 기존 트랜잭션 없이 단독 실행할 수 없다")
    void writerRequiresExistingTransaction() {
        assertThatThrownBy(() -> outboxEventWriter.write(
                OutboxEventType.USER_NICKNAME_UPDATED,
                OutboxAggregateType.USER,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new UserOutboxPayload(OutboxEventAction.UPDATED)
        )).isInstanceOf(IllegalTransactionStateException.class);
    }

    private void failSerialization() {
        given(payloadSerializer.serialize(any()))
                .willThrow(new OutboxPayloadSerializationException(
                        "TestPayload",
                        new IllegalArgumentException("serialization failed")
                ));
    }

    @TestConfiguration
    static class RollbackProbeConfiguration {

        @Bean
        RollbackProbeService rollbackProbeService(
                UserRepository userRepository,
                OutboxEventWriter outboxEventWriter
        ) {
            return new RollbackProbeService(userRepository, outboxEventWriter);
        }
    }

    static class RollbackProbeService {

        private final UserRepository userRepository;
        private final OutboxEventWriter outboxEventWriter;

        RollbackProbeService(
                UserRepository userRepository,
                OutboxEventWriter outboxEventWriter
        ) {
            this.userRepository = userRepository;
            this.outboxEventWriter = outboxEventWriter;
        }

        @Transactional
        public void saveUserAndOutbox(String email) {
            User user = userRepository.save(User.builder()
                    .email(email)
                    .nickname("rollback")
                    .password("password")
                    .build());
            outboxEventWriter.write(
                    OutboxEventType.USER_NICKNAME_UPDATED,
                    OutboxAggregateType.USER,
                    user.getId(),
                    user.getId(),
                    new UserOutboxPayload(OutboxEventAction.UPDATED)
            );
        }
    }
}
