package com.codeit.sb13.monew.interest.service;

import com.codeit.sb13.monew.interest.domain.Subscribe;
import com.codeit.sb13.monew.interest.repository.SubscribeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 새 구독 저장을 별도 트랜잭션으로 격리하기 위한 컴포넌트.
 *
 * <p>PostgreSQL은 트랜잭션 안의 한 문장이 제약 위반으로 실패하면, 그 트랜잭션은
 * 롤백 전까지 이후 어떤 조회도 실행할 수 없는 중단(aborted) 상태가 된다.
 * {@link SubscribeServiceImpl#subscribe}는 저장이 {@code uk_subscriptions_interest_user}
 * 위반으로 실패했을 때 곧바로 기존 구독을 다시 조회해야 하는데, 저장과 조회가
 * 같은 트랜잭션에 있으면 그 조회조차 실패한다. 그래서 저장 시도 자체를
 * {@link Propagation#REQUIRES_NEW}로 별도 트랜잭션에 격리해, 저장이 실패해
 * 롤백되어도 호출한 쪽의 트랜잭션은 영향을 받지 않도록 한다.</p>
 *
 * <p>{@code @Transactional(propagation = REQUIRES_NEW)}는 Spring이 만든 프록시를
 * 거쳐야만 적용된다. {@code SubscribeServiceImpl} 안에 이 로직을 private 메서드로
 * 두면 같은 객체 안에서의 호출(self-invocation)이라 프록시를 거치지 않고, 결국
 * REQUIRES_NEW가 적용되지 않는다. 그래서 별도 빈으로 분리했다.</p>
 */
@Component
@RequiredArgsConstructor
public class SubscribeSaver {

    private final SubscribeRepository subscribeRepository;

    /**
     * 구독을 별도 트랜잭션에서 저장한다.
     *
     * <p>{@code uk_subscriptions_interest_user} 유니크 제약을 위반하면
     * {@link org.springframework.dao.DataIntegrityViolationException}이 발생하고,
     * 이 메서드의 트랜잭션만 롤백된다. 호출한 쪽의 트랜잭션은 이 실패의 영향을
     * 받지 않으므로, 그 안에서 바로 기존 구독을 다시 조회할 수 있다.</p>
     *
     * @param subscribe 저장할 구독
     * @return 저장된 구독
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Subscribe save(Subscribe subscribe) {
        return subscribeRepository.saveAndFlush(subscribe);
    }
}
