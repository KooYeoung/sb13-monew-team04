package com.codeit.sb13.monew.interest.domain;

import com.codeit.sb13.monew.global.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관심사({@link Interest})에 속한 키워드 엔티티.
 *
 * <p>{@link Interest}와 다대일 양방향 연관관계를 가진다. 이 연관관계는
 * {@link Interest}가 제공하는 메서드를 통해서만 맺어지고 끊어지도록
 * 설계되어 있어, 이 클래스의 생성자와 {@link #detachInterest()}는
 * 패키지 프라이빗으로 제한되어 있다.</p>
 */
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "keywords")
public class Keyword extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interest_id", nullable = false)
    private Interest interest;

    @Column(nullable = false, length = 50)
    private String keyword;

    /**
     * 소속될 관심사와 키워드 텍스트를 받아 키워드를 생성한다.
     *
     * <p>{@link Interest#addKeyword(String)}에서만 호출되도록
     * 패키지 프라이빗으로 제한한다.</p>
     *
     * @param interest 이 키워드가 소속될 관심사
     * @param keyword 키워드 텍스트
     */
    Keyword(Interest interest, String keyword) {
        this.interest = interest;
        this.keyword = keyword;
    }

    /**
     * 키워드 텍스트를 새 값으로 변경한다.
     *
     * @param keyword 변경할 새 키워드 텍스트
     */
    public void changeKeyword(String keyword) {
        this.keyword = keyword;
    }

    /**
     * 소속된 관심사와의 연관관계를 끊는다.
     *
     * <p>{@link Interest#removeKeyword(Keyword)}가 컬렉션에서 이 키워드를
     * 실제로 제거하는 데 성공했을 때만 호출되어야 한다. 그 외의 경로에서
     * 임의로 호출하면 양방향 연관관계의 정합성이 깨질 수 있으므로
     * 패키지 프라이빗으로 제한한다.</p>
     */
    void detachInterest() {
        this.interest = null;
    }
}
