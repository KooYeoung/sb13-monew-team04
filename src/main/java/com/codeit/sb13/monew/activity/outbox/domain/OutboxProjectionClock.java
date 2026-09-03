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

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(name = "current_version", nullable = false)
    private long currentVersion;

    /**
     * 애플리케이션 singleton 식별자를 검증하고 다음 전역 projection 버전을 발급한다.
     *
     * @return 현재 값보다 1 큰 projection 버전
     * @throws IllegalStateException singleton 식별자가 {@value #SINGLETON_ID}이 아닌 경우
     */
    public long nextVersion() {
        if (id == null || id.longValue() != SINGLETON_ID) {
            throw new IllegalStateException(
                    "Outbox projection clock id는 " + SINGLETON_ID + "이어야 합니다.");
        }
        return ++currentVersion;
    }
}
