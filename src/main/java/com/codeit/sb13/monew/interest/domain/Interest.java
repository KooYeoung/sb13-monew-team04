package com.codeit.sb13.monew.interest.domain;

import com.codeit.sb13.monew.global.domain.UpdatedAtEntity;
import com.codeit.sb13.monew.global.exception.interest.InterestKeywordRequiredException;
import com.codeit.sb13.monew.global.exception.interest.InterestNameInvalidException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * 관심사(Interest) 애그리거트 루트.
 *
 * <p>이름과 키워드 목록을 가지며, {@link Keyword}와의 양방향 연관관계를
 * 스스로 관리한다. 키워드는 이 클래스가 제공하는 {@link #addKeyword(String)},
 * {@link #removeKeyword(Keyword)}를 통해서만 추가/제거되어야 하며,
 * 관심사는 항상 최소 1개의 키워드를 유지해야 한다는 불변조건을 가진다.</p>
 */
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "interests")
public class Interest extends UpdatedAtEntity {

    @Column(nullable = false, length = 50)
    private String name;

    @OneToMany(mappedBy = "interest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Keyword> keywords = new ArrayList<>();

    @Builder
    private Interest(String name) {
        this.name = validateName(name);
    }

    /**
     * 이름을 받아 새로운 관심사를 생성한다.
     *
     * <p>생성 직후에는 키워드 목록이 비어 있으므로, 키워드는 별도로
     * {@link #addKeyword(String)}를 호출해 추가해야 한다.</p>
     *
     * @param name 관심사 이름
     * @return 생성된 {@link Interest} 인스턴스
     */
    public static Interest create(String name) {
        return Interest.builder()
                .name(name)
                .build();
    }

    /**
     * 새 키워드를 추가하고, 추가된 키워드가 이 관심사를 양방향으로
     * 참조하도록 연관관계를 설정한다.
     *
     * @param keywordText 추가할 키워드 텍스트
     * @return 새로 생성되어 이 관심사에 추가된 {@link Keyword} 인스턴스
     */
    public Keyword addKeyword(String keywordText) {
        Keyword keyword = new Keyword(this, keywordText);
        this.keywords.add(keyword);
        return keyword;
    }

    /**
     * 지정한 키워드를 관심사의 키워드 목록에서 제거한다.
     *
     * <p>컬렉션에서 실제로 제거가 일어난 경우에만 해당 키워드의
     * {@code interest} 참조를 끊어({@link Keyword#detachInterest()}) 양방향
     * 연관관계의 정합성을 맞춘다. 이 관심사에 속하지 않은 키워드가 인자로
     * 들어오면 아무 일도 일어나지 않고 조용히 무시된다.</p>
     *
     * <p>관심사는 항상 최소 1개의 키워드를 유지해야 하므로, 남은 키워드가
     * 1개뿐인 상태에서 그 키워드를 제거하려 하면 예외를 던지고 제거를
     * 거부한다.</p>
     *
     * @param keyword 제거할 키워드
     * @throws InterestKeywordRequiredException 제거하려는 키워드가
     *         이 관심사에 남은 마지막 키워드인 경우
     */
    public void removeKeyword(Keyword keyword) {
        if (this.keywords.size() <= 1 && this.keywords.contains(keyword)) {
            throw new InterestKeywordRequiredException(this.getId());
        }

        boolean removed = this.keywords.remove(keyword);
        if (removed) {
            keyword.detachInterest();
        }
    }

    /**
     * 관심사 이름을 새 이름으로 변경한다.
     *
     * @param name 변경할 새 이름
     */
    public void changeName(String name) {
        this.name = validateName(name);
    }

    private static String validateName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new InterestNameInvalidException(name);
        }
        if (name.length() > 50) {
            throw new InterestNameInvalidException(name);
        }

        return name;
    }
}
