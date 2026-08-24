package com.codeit.sb13.monew.interest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.sb13.monew.interest.domain.Interest;
import com.codeit.sb13.monew.interest.domain.Subscribe;
import com.codeit.sb13.monew.interest.repository.SubscribeRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@code @Transactional(propagation = REQUIRES_NEW)}가 실제로 트랜잭션을
 * 분리하는지는 순수 단위 테스트로 검증할 수 없다(Spring 프록시가 개입해야
 * 의미가 있는 동작이라, 실제 확인하려면 프록시가 살아있는 통합 테스트가
 * 필요하다). 여기서는 {@link SubscribeSaver#save}가 {@link SubscribeRepository#saveAndFlush}에
 * 올바르게 위임하는지만 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class SubscribeSaverTest {

    @Mock
    SubscribeRepository subscribeRepository;

    @InjectMocks
    SubscribeSaver subscribeSaver;

    @Test
    @DisplayName("save()는 subscribeRepository.saveAndFlush()에 위임하고 그 결과를 그대로 반환한다")
    void save_delegatesToSaveAndFlush() {
        // given
        Interest interest = Interest.create("스포츠");
        Subscribe subscribe = Subscribe.of(interest, UUID.randomUUID());
        when(subscribeRepository.saveAndFlush(subscribe)).thenReturn(subscribe);

        // when
        Subscribe result = subscribeSaver.save(subscribe);

        // then
        assertThat(result).isEqualTo(subscribe);
        verify(subscribeRepository).saveAndFlush(subscribe);
    }
}
