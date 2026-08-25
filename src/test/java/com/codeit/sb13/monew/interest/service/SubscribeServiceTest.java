package com.codeit.sb13.monew.interest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.sb13.monew.global.exception.interest.InterestNotFoundException;
import com.codeit.sb13.monew.interest.controller.dto.SubscribeResponse;
import com.codeit.sb13.monew.interest.domain.Interest;
import com.codeit.sb13.monew.interest.domain.Subscribe;
import com.codeit.sb13.monew.interest.repository.InterestRepository;
import com.codeit.sb13.monew.interest.repository.SubscribeRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SubscribeServiceTest {

    @Mock
    InterestRepository interestRepository;

    @Mock
    SubscribeRepository subscribeRepository;

    @Mock
    SubscribeSaver subscribeSaver;

    @InjectMocks
    SubscribeServiceImpl subscribeServiceImpl;

    private Interest interestWithId(String name) {
        Interest interest = Interest.create(name);
        interest.addKeyword("키워드");
        ReflectionTestUtils.setField(interest, "id", UUID.randomUUID());
        return interest;
    }

    private Subscribe subscribeWithIdAndCreatedAt(Interest interest, UUID userId, LocalDateTime createdAt) {
        Subscribe subscribe = Subscribe.of(interest, userId);
        ReflectionTestUtils.setField(subscribe, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(subscribe, "createdAt", createdAt);
        return subscribe;
    }

    @Test
    @DisplayName("아직 구독하지 않은 관심사를 구독하면 새 구독을 저장하고 그 정보를 응답한다")
    void subscribe_notYetSubscribed_savesNewSubscription() {
        // given
        Interest interest = interestWithId("스포츠");
        UUID userId = UUID.randomUUID();

        when(interestRepository.findById(interest.getId())).thenReturn(Optional.of(interest));
        when(subscribeRepository.findByInterest_IdAndUserId(interest.getId(), userId))
                .thenReturn(Optional.empty());
        when(subscribeSaver.save(any(Subscribe.class))).thenAnswer(invocation -> {
            Subscribe saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.now());
            return saved;
        });
        when(subscribeRepository.countByInterest_Id(interest.getId())).thenReturn(1L);

        // when
        SubscribeResponse response = subscribeServiceImpl.subscribe(interest.getId(), userId);

        // then
        assertThat(response.interestId()).isEqualTo(interest.getId());
        assertThat(response.interestName()).isEqualTo("스포츠");
        assertThat(response.interestSubscriberCount()).isEqualTo(1L);
        assertThat(response.id()).isNotNull();
        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 구독 중인 관심사를 다시 구독 요청하면 새로 저장하지 않고 기존 구독 정보를 그대로 응답한다")
    void subscribe_alreadySubscribed_returnsExistingSubscriptionWithoutSaving() {
        // given
        Interest interest = interestWithId("스포츠");
        UUID userId = UUID.randomUUID();
        Subscribe existing = subscribeWithIdAndCreatedAt(interest, userId, LocalDateTime.now().minusDays(1));

        when(interestRepository.findById(interest.getId())).thenReturn(Optional.of(interest));
        when(subscribeRepository.findByInterest_IdAndUserId(interest.getId(), userId))
                .thenReturn(Optional.of(existing));
        when(subscribeRepository.countByInterest_Id(interest.getId())).thenReturn(3L);

        // when
        SubscribeResponse response = subscribeServiceImpl.subscribe(interest.getId(), userId);

        // then
        assertThat(response.id()).isEqualTo(existing.getId());
        assertThat(response.createdAt()).isEqualTo(existing.getCreatedAt());
        verify(subscribeSaver, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 관심사를 구독하려 하면 InterestNotFoundException을 던지고 구독을 조회/저장하지 않는다")
    void subscribe_nonExistingInterest_throwsExceptionAndDoesNothing() {
        // given
        UUID interestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(interestRepository.findById(interestId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> subscribeServiceImpl.subscribe(interestId, userId))
                .isInstanceOf(InterestNotFoundException.class);

        verify(subscribeRepository, never()).findByInterest_IdAndUserId(any(), any());
        verify(subscribeSaver, never()).save(any());
    }

    @Test
    @DisplayName("확인과 저장 사이에 경쟁이 발생해 저장 시점에 유니크 제약을 위반하면, 예외 대신 이미 저장된 구독을 다시 조회해 응답한다")
    void subscribe_raceConditionOnSave_returnsSubscriptionSavedByConcurrentRequest() {
        // given
        Interest interest = interestWithId("스포츠");
        UUID userId = UUID.randomUUID();
        Subscribe savedByOtherRequest = subscribeWithIdAndCreatedAt(interest, userId, LocalDateTime.now());

        when(interestRepository.findById(interest.getId())).thenReturn(Optional.of(interest));
        when(subscribeRepository.findByInterest_IdAndUserId(interest.getId(), userId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(savedByOtherRequest));
        when(subscribeSaver.save(any(Subscribe.class)))
                .thenThrow(new DataIntegrityViolationException("uk_subscriptions_interest_user"));
        when(subscribeRepository.countByInterest_Id(interest.getId())).thenReturn(1L);

        // when
        SubscribeResponse response = subscribeServiceImpl.subscribe(interest.getId(), userId);

        // then
        assertThat(response.id()).isEqualTo(savedByOtherRequest.getId());
    }

    @Test
    @DisplayName("경쟁 상황이 아닌 다른 원인으로 저장이 실패하면 원래 예외를 그대로 던진다")
    void subscribe_unrelatedSaveFailure_rethrowsOriginalException() {
        // given
        Interest interest = interestWithId("스포츠");
        UUID userId = UUID.randomUUID();
        DataIntegrityViolationException original = new DataIntegrityViolationException("unrelated constraint");

        when(interestRepository.findById(interest.getId())).thenReturn(Optional.of(interest));
        when(subscribeRepository.findByInterest_IdAndUserId(interest.getId(), userId))
                .thenReturn(Optional.empty());
        when(subscribeSaver.save(any(Subscribe.class))).thenThrow(original);

        // when & then
        assertThatThrownBy(() -> subscribeServiceImpl.subscribe(interest.getId(), userId))
                .isSameAs(original);
    }

    @Test
    @DisplayName("구독 중인 관심사의 구독을 취소하면 해당 구독을 삭제한다")
    void unsubscribe_subscribed_deletesSubscription() {
        // given
        UUID interestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(interestRepository.existsById(interestId)).thenReturn(true);

        // when
        subscribeServiceImpl.unsubscribe(interestId, userId);

        // then
        verify(subscribeRepository).deleteByInterest_IdAndUserId(interestId, userId);
    }

    @Test
    @DisplayName("구독하지 않은 관심사에 대한 구독 취소 요청도 에러 없이 성공한다")
    void unsubscribe_notSubscribed_succeedsWithoutError() {
        // given
        UUID interestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(interestRepository.existsById(interestId)).thenReturn(true);

        // when & then
        subscribeServiceImpl.unsubscribe(interestId, userId);

        verify(subscribeRepository).deleteByInterest_IdAndUserId(interestId, userId);
    }

    @Test
    @DisplayName("존재하지 않는 관심사의 구독을 취소하려 하면 InterestNotFoundException을 던지고 삭제하지 않는다")
    void unsubscribe_nonExistingInterest_throwsExceptionAndDoesNotDelete() {
        // given
        UUID interestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(interestRepository.existsById(interestId)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> subscribeServiceImpl.unsubscribe(interestId, userId))
                .isInstanceOf(InterestNotFoundException.class);

        verify(subscribeRepository, never()).deleteByInterest_IdAndUserId(any(), any());
    }
}
