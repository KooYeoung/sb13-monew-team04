package com.codeit.sb13.monew.interest.service;

import com.codeit.sb13.monew.global.exception.interest.InterestNotFoundException;
import com.codeit.sb13.monew.interest.controller.dto.SubscribeResponse;
import com.codeit.sb13.monew.interest.domain.Interest;
import com.codeit.sb13.monew.interest.domain.Subscribe;
import com.codeit.sb13.monew.interest.repository.InterestRepository;
import com.codeit.sb13.monew.interest.repository.SubscribeRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SubscribeServiceImpl implements SubscribeService {

    private final InterestRepository interestRepository;
    private final SubscribeRepository subscribeRepository;
    private final SubscribeSaver subscribeSaver;

    /**
     * {@inheritDoc}
     *
     * <p>{@link SubscribeRepository#findByInterest_IdAndUserId}로 이미 구독
     * 중인지 먼저 확인해, 있으면 그 구독을 그대로 쓰고 없으면 새로 만든다.
     * 이 확인과 실제 저장 사이에는 경쟁 구간이 존재한다. 같은 사용자가 거의
     * 동시에 두 번 구독 요청을 보내면 둘 다 "아직 구독 안 함"으로 확인하고
     * 둘 다 저장을 시도할 수 있는데, {@code uk_subscriptions_interest_user}
     * 유니크 제약이 최후 방어선 역할을 한다.</p>
     *
     * <p>저장은 {@link SubscribeSaver#save}를 통해 별도 트랜잭션에서 실행된다.
     * PostgreSQL은 제약 위반으로 실패한 트랜잭션을 롤백 전까지 조회조차 할 수
     * 없는 상태로 만들기 때문에, 저장을 이 메서드의 트랜잭션과 같은 곳에서
     * 시도하면 실패 직후 기존 구독을 다시 조회하는 것 자체가 불가능하다.
     * {@link #saveSubscribe}에서 이 실패를 잡아 기존 구독을 다시 조회해
     * 반환한다.</p>
     */
    @Override
    public SubscribeResponse subscribe(UUID interestId, UUID userId) {
        Interest interest = interestRepository.findById(interestId)
                .orElseThrow(() -> new InterestNotFoundException(interestId));

        Subscribe subscribe = subscribeRepository.findByInterest_IdAndUserId(interestId, userId)
                .orElseGet(() -> saveSubscribe(interest, userId));

        long subscriberCount = subscribeRepository.countByInterest_Id(interestId);
        return SubscribeResponse.of(subscribe, subscriberCount);
    }

    /**
     * {@inheritDoc}
     *
     * <p>구독 존재 여부는 확인하지 않고 바로 삭제를 시도한다.
     * {@link SubscribeRepository#deleteByInterest_IdAndUserId}는 삭제할 행이
     * 없어도 예외 없이 끝나므로, 구독하지 않은 상태에서 호출해도 그대로
     * 성공(멱등)한다.</p>
     */
    @Override
    public void unsubscribe(UUID interestId, UUID userId) {
        if (!interestRepository.existsById(interestId)) {
            throw new InterestNotFoundException(interestId);
        }
        subscribeRepository.deleteByInterest_IdAndUserId(interestId, userId);
    }

    /**
     * 새 구독을 저장한다.
     *
     * <p>저장 시점에 {@code uk_subscriptions_interest_user} 유니크 제약 위반이
     * 나면, 확인과 저장 사이의 경쟁으로 다른 요청이 먼저 저장에 성공한 것이므로
     * 그 구독을 다시 조회해 반환한다. 조회했는데도 없다면(다른 원인으로 인한
     * 제약 위반이라면) 원래 예외를 그대로 던진다.
     */
    private Subscribe saveSubscribe(Interest interest, UUID userId) {
        try {
            return subscribeSaver.save(Subscribe.of(interest, userId));
        } catch (DataIntegrityViolationException e) {
            return subscribeRepository.findByInterest_IdAndUserId(interest.getId(), userId)
                    .orElseThrow(() -> e);
        }
    }
}
