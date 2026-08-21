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
}
