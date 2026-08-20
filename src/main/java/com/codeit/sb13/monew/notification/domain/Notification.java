package com.codeit.sb13.monew.notification.domain;

import com.codeit.sb13.monew.global.domain.UpdatedAtEntity;
import com.codeit.sb13.monew.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends UpdatedAtEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private UUID resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceType resourceType;

    @Column(nullable = false)
    private boolean confirmed;

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

    public void confirm() {
        this.confirmed = true;
    }
}
