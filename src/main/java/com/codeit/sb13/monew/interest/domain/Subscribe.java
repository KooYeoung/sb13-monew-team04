package com.codeit.sb13.monew.interest.domain;

import com.codeit.sb13.monew.global.domain.CreatedAtEntity;
import com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus;
import jakarta.persistence.*;

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
        indexes = {
                @Index(name = "idx_subscriptions_user_created_id", columnList = "user_id, created_at DESC, id DESC")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_subscriptions_interest_user",
                columnNames = {"interest_id", "user_id"}
        )
)
public class Subscribe extends CreatedAtEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interest_id", nullable = false)
    private Interest interest;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility_status", nullable = false, length = 50)
    private ActivityVisibilityStatus visibilityStatus;

    @Builder
    private Subscribe(Interest interest, UUID userId) {
        this.interest = interest;
        this.userId = userId;
        this.visibilityStatus = ActivityVisibilityStatus.ACTIVE;
    }

    public static Subscribe of(Interest interest, UUID userId) {
        return Subscribe.builder()
                .interest(interest)
                .userId(userId)
                .build();
    }
}
