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

    Keyword(Interest interest, String keyword) {
        this.interest = interest;
        this.keyword = keyword;
    }

    public void changeKeyword(String keyword) {
        this.keyword = keyword;
    }

    void detachInterest() {
        this.interest = null;
    }
}
