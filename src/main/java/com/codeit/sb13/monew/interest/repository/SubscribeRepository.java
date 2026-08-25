package com.codeit.sb13.monew.interest.repository;

import com.codeit.sb13.monew.interest.domain.Subscribe;
import com.codeit.sb13.monew.interest.repository.dto.InterestSubscriberRow;
import com.codeit.sb13.monew.interest.repository.dto.SubscribedInterestActivityProjection;
import com.codeit.sb13.monew.user.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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


    void deleteByUserId(UUID userId);

    /**
     * 특정 사용자의 특정 관심사에 대한 구독을 삭제한다.
     *
     * <p>{@link com.codeit.sb13.monew.interest.service.SubscribeServiceImpl#unsubscribe}가
     * 구독 취소 요청을 처리할 때 쓴다. 구독하지 않은 상태에서 호출해도 삭제되는 행이
     * 없을 뿐 예외 없이 끝나므로(멱등), 구독 여부를 미리 확인할 필요가 없다.</p>
     *
     * @param interestId 구독을 취소할 관심사의 id
     * @param userId 구독을 취소하는 사용자의 id
     */
    void deleteByInterest_IdAndUserId(UUID interestId, UUID userId);

    /**
     * 특정 관심사를 구독 중인 사용자 수를 센다.
     *
     * @param interestId 구독자 수를 셀 관심사의 id
     * @return 해당 관심사를 구독 중인 사용자 수
     */
    long countByInterest_Id(UUID interestId);

    /**
     * 특정 사용자가 특정 관심사를 이미 구독하고 있는지 조회한다.
     *
     * <p>{@link com.codeit.sb13.monew.interest.service.SubscribeServiceImpl#subscribe}가
     * 중복 구독을 막기 위해, 새 구독을 저장하기 전에 이 메서드로 기존 구독이
     * 있는지 먼저 확인한다.</p>
     *
     * @param interestId 관심사 id
     * @param userId 사용자 id
     * @return 이미 존재하는 구독. 없으면 빈 {@link Optional}
     */
    Optional<Subscribe> findByInterest_IdAndUserId(UUID interestId, UUID userId);

    /**
     * 여러 관심사를 구독 중인 (논리 삭제되지 않은) 사용자들을, 관심사 id와 사용자를
     * 한 쌍씩 묶어 한 번의 쿼리로 조회한다.
     *
     * <p>신규 기사가 여러 관심사의 키워드와 동시에 매칭됐을 때,
     * {@link com.codeit.sb13.monew.interest.service.InterestServiceImpl#notifyForNewArticles}가
     * 매칭된 관심사 수만큼 이 조회를 반복하지 않고, 매칭된 관심사 id 전체를 모아 이
     * 메서드를 한 번만 호출해 알림 수신자를 가져온다. {@code Subscribe}는 {@code User}를
     * 정식 연관관계로 갖지 않고 {@code userId} 값만 가지므로, 이 조회에서
     * {@code User}를 직접 조인해 논리 삭제된 사용자를 걸러낸다.</p>
     *
     * @param interestIds 구독자를 조회할 관심사 id 목록
     * @return 관심사 id와 그 관심사를 구독 중인, 논리 삭제되지 않은 사용자를 한 쌍씩 담은 목록.
     *         호출부에서 {@code interestId} 기준으로 그룹화해 사용한다.
     */
    @Query("""
        SELECT new com.codeit.sb13.monew.interest.repository.dto.InterestSubscriberRow(s.interest.id, u)
        FROM Subscribe s
        JOIN User u ON u.id = s.userId
        WHERE s.interest.id IN :interestIds
            AND u.deletedAt IS NULL
    """)
    List<InterestSubscriberRow> findSubscriberUsersByInterestIds(@Param("interestIds") List<UUID> interestIds);

    /**
     * 현재 구독자가 있는 관심사들의 키워드를, 어느 관심사의 키워드인지 구분 없이
     * 중복 제거해 조회한다.
     *
     * <p>{@link com.codeit.sb13.monew.interest.service.InterestServiceImpl#findSubscribedKeywords}가
     * 네이버 뉴스 검색어를 정하기 위해 이 메서드를 쓴다. 구독자가 논리 삭제된 경우는
     * 구독이 없는 것으로 본다.</p>
     *
     * @return 구독 중인 관심사들의 키워드 문자열 목록 (중복 제거, 순서는 보장하지 않는다)
     */
    @Query("""
        SELECT DISTINCT k.keyword
        FROM Subscribe s
        JOIN s.interest i
        JOIN i.keywords k
        JOIN User u ON u.id = s.userId
        WHERE u.deletedAt IS NULL
    """)
    List<String> findDistinctKeywordsOfSubscribedInterests();

    /**
     * 사용자 활동내역의 "구독 중인 관심사" 영역에 내려줄 현재 구독 목록을 조회한다.
     *
     * <p>이 조회는 최근 활동 10건처럼 자르는 목록이 아니라, 요청 사용자가 현재 구독 중인
     * 관심사 전체를 반환한다. 구독 해제는 {@code subscriptions} 행이 물리적으로 사라진
     * 상태로 보므로, 구독 행이 없는 관심사는 자연스럽게 결과에서 제외된다.</p>
     *
     * <p>요청 사용자 자체가 논리삭제된 경우에는 활동내역을 보여주지 않기 위해 결과를
     * 반환하지 않는다. 관심사별 구독자 수 역시 논리삭제되지 않은 사용자들의 구독만
     * 집계한다.</p>
     *
     * @param userId 활동내역을 조회할 사용자 id
     * @return 사용자가 현재 구독 중인 관심사 목록
     */
    @Query("""
        SELECT new com.codeit.sb13.monew.interest.repository.dto.SubscribedInterestActivityProjection(
            s.id,
            s.createdAt,
            i,
            (SELECT COUNT(s2)
             FROM Subscribe s2
             JOIN User u2 ON s2.userId = u2.id
             JOIN s2.interest i2
             WHERE i2.id = i.id
                 AND u2.deletedAt IS NULL)
            )
        FROM Subscribe s
        JOIN s.interest i
        JOIN User u ON u.id = s.userId
        WHERE s.userId = :userId
            AND u.deletedAt IS NULL
        ORDER BY s.createdAt DESC, s.id DESC
    """)
    List<SubscribedInterestActivityProjection> findSubscribedInterestActivities(@Param("userId") UUID userId);
}
