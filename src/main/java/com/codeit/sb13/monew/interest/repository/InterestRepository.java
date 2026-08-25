package com.codeit.sb13.monew.interest.repository;

import com.codeit.sb13.monew.interest.domain.Interest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface InterestRepository extends JpaRepository<Interest, UUID>, InterestRepositoryCustom {

    /**
     * 주어진 이름을 가진 관심사가 이미 존재하는지 확인한다.
     *
     * <p>관심사 등록 시 이름 중복 검사에 사용된다. 엔티티 전체를 조회하지 않고
     * 존재 여부만 확인하므로 단순 조회보다 가볍다.</p>
     *
     * @param name 확인할 관심사 이름
     * @return 동일한 이름의 관심사가 이미 존재하면 {@code true}
     */
    boolean existsByName(String name);

    /**
     * 길이가 {@code minLength}~{@code maxLength} 사이인 관심사 이름만 조회한다.
     *
     * <p>관심사 등록 시 새 이름이 기존 이름과 80% 이상 유사한지 비교하는 데
     * 쓰인다. Levenshtein 유사도는 {@code 1 - 편집거리 / max(두 문자열 길이)}로
     * 계산되는데, 편집 거리는 두 문자열의 길이 차이보다 작을 수 없다. 그래서
     * 새 이름 길이가 {@code L}일 때 유사도가 0.8 이상이 되려면 비교 대상 이름의
     * 길이가 반드시 {@code [ceil(0.8L), floor(L/0.8)]} 범위 안에 있어야 하고,
     * 이 범위 밖의 이름은 계산해볼 필요도 없이 걸러낼 수 있다. 호출하는 쪽
     * ({@link com.codeit.sb13.monew.interest.service.InterestServiceImpl})이
     * 이 범위를 계산해 넘긴다.</p>
     *
     * <p>{@code length(i.name)} 조건만으로 DB가 반드시 후보군만 스캔한다고
     * 보장하지는 않는다. 인덱스 유무와 실행 계획에 따라 여전히 테이블 전체를
     * 스캔할 수 있다. 이 조회가 확실히 보장하는 건 애플리케이션으로 돌아오는
     * 이름의 개수와, 그로 인해 뒤에서 수행할 Levenshtein 비교 횟수를 줄인다는
     * 점이다. DB 쪽 스캔 자체를 줄이려면 {@code length(name)}에 대한 별도
     * 인덱스가 필요하다.</p>
     *
     * @param minLength 조회할 이름 길이의 하한(포함)
     * @param maxLength 조회할 이름 길이의 상한(포함)
     * @return 길이 조건을 만족하는 관심사 이름 목록
     */
    @Query("select i.name from Interest i where length(i.name) between :minLength and :maxLength")
    List<String> findNamesByLengthBetween(@Param("minLength") int minLength, @Param("maxLength") int maxLength);

    /**
     * 모든 관심사를 키워드까지 함께 조회한다.
     *
     * <p>{@link com.codeit.sb13.monew.interest.service.InterestServiceImpl#notifyForNewArticles}가
     * 신규 기사와 매칭되는지 판단하려면 결국 모든 관심사의 {@code keywords}를 순회해야 한다.
     * {@code Interest.keywords}는 지연 로딩에 {@code @BatchSize(size = 100)}가 붙어 있어
     * 관심사 하나마다 별도 조회가 나가지는 않지만, 관심사가 100개를 넘으면 배치 로딩
     * 쿼리 자체가 {@code ceil(전체 관심사 수 / 100)}회로 늘어난다. 이 메서드는 fetch join으로
     * 관심사와 키워드를 한 번의 쿼리에 담아, 관심사 수와 무관하게 조회가 정확히 한 번만
     * 실행되게 한다.</p>
     *
     * <p>{@link InterestRepositoryCustomImpl#search}가 검색 결과에는 fetch join을 쓰지
     * 않는 것과는 상황이 다르다. 거기서는 {@code LIMIT}으로 페이지 크기를 자르는데, 1:N
     * 조인은 관심사 하나를 키워드 수만큼 여러 행으로 부풀려(fan-out) 그 LIMIT이 관심사
     * 개수가 아니라 조인된 행 개수를 자르게 만든다. 이 메서드는 페이지네이션 없이 전체를
     * 가져오므로 그 문제가 없고, {@code DISTINCT}로 부풀려진 행을 다시 관심사 1건당
     * 1개체로 되돌린다.</p>
     *
     * @return 키워드까지 함께 로딩된 전체 관심사 목록
     */
    @Query("SELECT DISTINCT i FROM Interest i LEFT JOIN FETCH i.keywords")
    List<Interest> findAllWithKeywords();
}
