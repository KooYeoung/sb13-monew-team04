package com.codeit.sb13.monew.interest.domain;

import com.codeit.sb13.monew.global.domain.UpdatedAtEntity;
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
        this.name = name;
    }

    public static Interest create(String name) {
        return Interest.builder()
                .name(name)
                .build();
    }

    public Keyword addKeyword(String keywordText) {
        Keyword keyword = new Keyword(this, keywordText);
        this.keywords.add(keyword);
        return keyword;
    }

    public void removeKeyword(Keyword keyword) {
        this.keywords.remove(keyword);
    }

    public void changeName(String name) {
        this.name = name;
    }
}
