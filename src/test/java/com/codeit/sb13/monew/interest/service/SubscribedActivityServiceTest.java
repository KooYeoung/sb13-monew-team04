package com.codeit.sb13.monew.interest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.codeit.sb13.monew.interest.domain.Interest;
import com.codeit.sb13.monew.interest.repository.SubscribeRepository;
import com.codeit.sb13.monew.interest.repository.dto.SubscribedInterestActivityProjection;
import com.codeit.sb13.monew.interest.service.dto.SubscribedInterestActivityDto;
import com.codeit.sb13.monew.interest.service.impl.SubscribedActivityService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SubscribedActivityServiceTest {

    @Mock
    private SubscribeRepository subscribeRepository;

    @InjectMocks
    private SubscribedActivityService subscribedActivityService;

    @Test
    @DisplayName("getSubscribedInterestActivities maps repository projections to DTOs")
    void getSubscribedInterestActivities_mapsProjectionToDto() {
        UUID userId = UUID.randomUUID();
        UUID interestId = UUID.randomUUID();
        Interest interest = Interest.create("sports");
        ReflectionTestUtils.setField(interest, "id", interestId);
        interest.addKeyword("football");
        interest.addKeyword("baseball");
        SubscribedInterestActivityProjection projection = new SubscribedInterestActivityProjection(
                UUID.randomUUID(),
                LocalDateTime.of(2026, 8, 25, 13, 0),
                interest,
                12L
        );
        given(subscribeRepository.findSubscribedInterestActivities(userId)).willReturn(List.of(projection));

        List<SubscribedInterestActivityDto> result =
                subscribedActivityService.getSubscribedInterestActivities(userId);

        assertThat(result).singleElement()
                .satisfies(dto -> {
                    assertThat(dto.id()).isEqualTo(projection.id());
                    assertThat(dto.createdAt()).isEqualTo(projection.createdAt());
                    assertThat(dto.interestId()).isEqualTo(interestId);
                    assertThat(dto.interestName()).isEqualTo("sports");
                    assertThat(dto.interestKeywords()).containsExactly("football", "baseball");
                    assertThat(dto.interestSubscriberCount()).isEqualTo(12L);
                });
        then(subscribeRepository).should().findSubscribedInterestActivities(userId);
    }
}
