package com.codeit.sb13.monew.interest.repository;

import com.codeit.sb13.monew.interest.domain.Subscribe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SubscribeRepository extends JpaRepository<Subscribe, UUID> {

    /**
     * 특정 관심사에 대한 구독 레코드를 모두 삭제한다.
     *
     * <p>{@code Interest}는 {@code Subscribe}를 {@code @OneToMany}로 들고 있지 않아
     * (단방향으로 {@code Subscribe -> Interest}만 참조한다) 관심사를 삭제해도
     * 구독 레코드가 함께 지워지도록 cascade를 걸 수 없다. {@code subscriptions.interest_id}에는
     * {@code ON DELETE CASCADE}가 없는 외래키 제약도 걸려 있어, 구독이 남아 있는 상태로
     * 관심사를 삭제하려 하면 제약 위반이 난다. 그래서 관심사를 물리 삭제하기 전에
     * {@link com.codeit.sb13.monew.interest.service.InterestServiceImpl#delete}에서
     * 이 메서드로 관련 구독을 먼저 지운다.</p>
     *
     * @param interestId 구독을 모두 지울 관심사의 id
     */
    void deleteByInterest_Id(UUID interestId);
}
