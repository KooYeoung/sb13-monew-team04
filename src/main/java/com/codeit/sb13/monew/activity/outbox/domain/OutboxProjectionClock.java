package com.codeit.sb13.monew.activity.outbox.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Outbox 이벤트의 전역 projection 순서를 발급하는 singleton row다.
 *
 * <p>이 row를 비관적 쓰기 잠금으로 변경한 트랜잭션만 다음 버전을 받을 수 있다.
 * 잠금은 원본 도메인 변경과 Outbox 저장이 commit될 때까지 유지되므로, 발급 순서와
 * commit 순서가 일치한다.</p>
 */
@Entity
@Getter
@Table(name = "outbox_projection_clock")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxProjectionClock {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(name = "current_version", nullable = false)
    private long currentVersion;

    /** 다음 전역 projection 버전을 발급한다. */
    public long nextVersion() {
        return ++currentVersion;
    }
}
