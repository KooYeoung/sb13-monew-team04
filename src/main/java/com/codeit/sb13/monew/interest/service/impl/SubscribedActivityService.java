package com.codeit.sb13.monew.interest.service.impl;

import com.codeit.sb13.monew.interest.repository.SubscribeRepository;
import com.codeit.sb13.monew.interest.service.dto.SubscribedInterestActivityDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscribedActivityService {
    private final SubscribeRepository subscribeRepository;

    public List<SubscribedInterestActivityDto> getSubscribedInterestActivities(UUID userId) {
        return subscribeRepository.findSubscribedInterestActivities(userId)
                .stream()
                .map(SubscribedInterestActivityDto::from)
                .toList();
    }
}
