package com.codeit.sb13.monew.interest.domain;

import com.codeit.sb13.monew.global.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(
        name = "subscriptions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_subscriptions_interest_user",
                columnNames = {"interest_id", "user_id"}
        )
)
public class Subscribe extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interest_id", nullable = false)
    private Interest interest;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Builder
    private Subscribe(Interest interest, UUID userId) {
        this.interest = interest;
        this.userId = userId;
    }

    public static Subscribe of(Interest interest, UUID userId) {
        return Subscribe.builder()
                .interest(interest)
                .userId(userId)
                .build();
    }
}
