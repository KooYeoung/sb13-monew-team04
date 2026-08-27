package com.codeit.sb13.monew.notification.domain;

import com.codeit.sb13.monew.global.domain.UpdatedAtEntity;
import com.codeit.sb13.monew.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends UpdatedAtEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String content;

    @Column(nullable = false)
    private UUID resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResourceType resourceType;

    @Column(nullable = false)
    private boolean confirmed;

    private LocalDateTime confirmedAt;

    @Builder
    private Notification(User user, String content, UUID resourceId, ResourceType resourceType) {
        this.user = user;
        this.content = content;
        this.resourceId = resourceId;
        this.resourceType = resourceType;
        this.confirmed = false;
    }

    public static Notification create(User user, String content, UUID resourceId, ResourceType resourceType) {
        return Notification.builder()
                .user(user)
                .content(content)
                .resourceId(resourceId)
                .resourceType(resourceType)
                .build();
    }

    public void confirm(LocalDateTime confirmedAt) {
        if (this.confirmed) {
            return;
        }
        this.confirmed = true;
        this.confirmedAt = confirmedAt;
    }

    public void confirm() {
        confirm(LocalDateTime.now());
    }
}
