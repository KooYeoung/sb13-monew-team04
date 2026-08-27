package com.codeit.sb13.monew.notification.repository;

import com.codeit.sb13.monew.global.config.QueryDslConfig;
import com.codeit.sb13.monew.notification.domain.Notification;
import com.codeit.sb13.monew.notification.domain.ResourceType;
import com.codeit.sb13.monew.notification.repository.dto.NotificationFindCondition;
import com.codeit.sb13.monew.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
@Import(QueryDslConfig.class)
@ActiveProfiles("test")
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TestEntityManager em;

    private User saveUser(String email, String nickname) {
        User user = User.builder().email(email).nickname(nickname).password("pw").build();
        return em.persistAndFlush(user);
    }

    private Notification saveNotification(User user, String content, LocalDateTime createdAt) {
        Notification notification = Notification.create(user, content, UUID.randomUUID(), ResourceType.COMMENT);
        em.persist(notification);
        em.flush();

        em.getEntityManager()
                .createNativeQuery("UPDATE notifications SET created_at = ?1 WHERE id = ?2")
                .setParameter(1, createdAt)
                .setParameter(2, notification.getId())
                .executeUpdate();
        em.clear();

        return em.find(Notification.class, notification.getId());
    }

    private Notification saveNotification(User user, String content, boolean confirmed, LocalDateTime confirmedAt) {
        Notification notification = Notification.create(user, content, UUID.randomUUID(), ResourceType.COMMENT);
        if (confirmed) {
            notification.confirm();
        }
        em.persist(notification);
        em.flush();

        em.getEntityManager()
                .createNativeQuery("UPDATE notifications SET confirmed_at = ?1 WHERE id = ?2")
                .setParameter(1, confirmedAt)
                .setParameter(2, notification.getId())
                .executeUpdate();
        em.clear();

        return em.find(Notification.class, notification.getId());
    }

    @Test
    @DisplayName("다른 사용자의 알림은 조회되지 않는다")
    void 사용자_격리() {
        // given
        User me = saveUser("me@test.com", "나");
        User other = saveUser("other@test.com", "다른사람");

        saveNotification(me, "내 알림", LocalDateTime.now());
        saveNotification(other, "다른 사람 알림", LocalDateTime.now());

        // when
        List<Notification> result = notificationRepository.findUnconfirmedByUserWithCursor(
                new NotificationFindCondition(me.getId(), null, null, 10));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUser().getId()).isEqualTo(me.getId());
    }

    @Test
    @DisplayName("confirmed가 true인 알림은 조회되지 않는다")
    void confirmed_true인_알림_제외() {
        // given
        User user = saveUser("me@test.com", "나");

        Notification unconfirmed = saveNotification(user, "미확인", LocalDateTime.now());
        Notification confirmed = saveNotification(user, "확인함", LocalDateTime.now());
        confirmed.confirm();
        em.flush();

        // when
        List<Notification> result = notificationRepository.findUnconfirmedByUserWithCursor(
                new NotificationFindCondition(user.getId(), null, null, 10));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(unconfirmed.getId());
    }

    @Test
    @DisplayName("createdAt이 같으면 id 기준으로 정렬되고, 커서 이전 항목은 제외된다")
    void 복합_커서_정렬과_제외() {
        // given
        User user = saveUser("me@test.com", "나");
        LocalDateTime sameTime = LocalDateTime.now().minusHours(1);
        saveNotification(user, "1", sameTime);
        saveNotification(user, "2", sameTime);

        // when: 커서 없이 조회하면 createdAt이 같은 두 건이 DB 정렬 순서(id 내림차순)로 나온다
        List<Notification> firstPage = notificationRepository.findUnconfirmedByUserWithCursor(
                new NotificationFindCondition(user.getId(), null, null, 10));

        // then
        assertThat(firstPage).hasSize(2);
        Notification newer = firstPage.get(0);
        Notification older = firstPage.get(1);

        // when: newer를 커서로 넘기면 newer 자신과 그 이전 항목은 제외되고 older만 남는다
        List<Notification> nextPage = notificationRepository.findUnconfirmedByUserWithCursor(
                new NotificationFindCondition(user.getId(), newer.getId(), newer.getCreatedAt(), 10));

        // then
        assertThat(nextPage).extracting(Notification::getId).containsExactly(older.getId());
    }

    @Test
    @DisplayName("limit을 넘는 개수는 조회되지 않는다")
    void limit_적용() {
        // given
        User user = saveUser("me@test.com", "나");
        for (int i = 0; i < 5; i++) {
            saveNotification(user, "알림" + i, LocalDateTime.now().minusMinutes(i));
        }

        // when
        List<Notification> result = notificationRepository.findUnconfirmedByUserWithCursor(
                new NotificationFindCondition(user.getId(), null, null, 3));

        // then
        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("확인 처리된 지 7일이 지난 알림은 삭제된다")
    void 확인_후_7일_지난_알림_삭제() {
        // given
        User user = saveUser("me@test.com", "나");
        LocalDateTime eightDaysAgo = LocalDateTime.now().minusDays(8);
        Notification expired = saveNotification(user, "확인된 지 오래됨", true, eightDaysAgo);

        LocalDateTime threshold = LocalDateTime.now().minusDays(7);

        // when
        int deletedCount = notificationRepository.deleteConfirmedBefore(threshold);

        // then
        assertThat(deletedCount).isEqualTo(1);
        assertThat(notificationRepository.findById(expired.getId())).isEmpty();
    }

    @Test
    @DisplayName("확인 처리된 지 7일이 안 지난 알림은 삭제되지 않는다")
    void 확인_후_7일_안지난_알림_유지() {
        // given
        User user = saveUser("me@test.com", "나");
        LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);
        Notification recent = saveNotification(user, "최근 확인함", true, threeDaysAgo);

        LocalDateTime threshold = LocalDateTime.now().minusDays(7);

        // when
        int deletedCount = notificationRepository.deleteConfirmedBefore(threshold);

        // then
        assertThat(deletedCount).isZero();
        assertThat(notificationRepository.findById(recent.getId())).isPresent();
    }

    @Test
    @DisplayName("미확인 알림은 확인 시각(confirmedAt)이 오래돼도 삭제 대상에서 제외된다")
    void 미확인_알림_삭제_제외() {
        // given
        User user = saveUser("me@test.com", "나");
        LocalDateTime tenDaysAgo = LocalDateTime.now().minusDays(10);
        Notification unconfirmed = saveNotification(user, "미확인", false, tenDaysAgo);

        LocalDateTime threshold = LocalDateTime.now().minusDays(7);

        // when
        int deletedCount = notificationRepository.deleteConfirmedBefore(threshold);

        // then
        assertThat(deletedCount).isZero();
        assertThat(notificationRepository.findById(unconfirmed.getId())).isPresent();
    }

    @Test
    @DisplayName("요청자의 미확인 알림이 모두 확인 처리되고, 전달한 시각이 confirmedAt으로 저장된다")
    void 벌크_확인_처리_성공() {
        // given
        User user = saveUser("me@test.com", "나");
        Notification n1 = saveNotification(user, "알림1", LocalDateTime.now());
        Notification n2 = saveNotification(user, "알림2", LocalDateTime.now());
        LocalDateTime now = LocalDateTime.now();

        // when
        int updatedCount = notificationRepository.confirmAllByUserId(user.getId(), List.of(n1.getId(), n2.getId()), now);

        // then
        assertThat(updatedCount).isEqualTo(2);

        Notification reloaded1 = notificationRepository.findById(n1.getId()).orElseThrow();
        Notification reloaded2 = notificationRepository.findById(n2.getId()).orElseThrow();
        assertThat(reloaded1.isConfirmed()).isTrue();
        assertThat(reloaded1.getConfirmedAt()).isCloseTo(now, within(1, ChronoUnit.SECONDS));
        assertThat(reloaded2.isConfirmed()).isTrue();
        assertThat(reloaded2.getConfirmedAt()).isCloseTo(now, within(1, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("다른 사용자의 알림은 확인 처리되지 않는다")
    void 벌크_확인_처리_사용자_격리() {
        // given
        User me = saveUser("me@test.com", "나");
        User other = saveUser("other@test.com", "다른사람");
        Notification myNotification = saveNotification(me, "내 알림", LocalDateTime.now());
        Notification othersNotification = saveNotification(other, "다른 사람 알림", LocalDateTime.now());

        // when
        notificationRepository.confirmAllByUserId(me.getId(), List.of(myNotification.getId()), LocalDateTime.now());

        // then
        Notification reloaded = notificationRepository.findById(othersNotification.getId()).orElseThrow();
        assertThat(reloaded.isConfirmed()).isFalse();
    }

    @Test
    @DisplayName("이미 확인된 알림은 확인 시각이 갱신되지 않고 확인 대상에서도 제외된다")
    void 벌크_확인_처리_이미_확인된_알림_제외() {
        // given
        User user = saveUser("me@test.com", "나");
        LocalDateTime originalConfirmedAt = LocalDateTime.now().minusDays(1);
        Notification alreadyConfirmed = saveNotification(user, "이미 확인함", true, originalConfirmedAt);

        // when
        int updatedCount = notificationRepository.confirmAllByUserId(user.getId(), List.of(alreadyConfirmed.getId()), LocalDateTime.now());

        // then
        assertThat(updatedCount).isZero();
        Notification reloaded = notificationRepository.findById(alreadyConfirmed.getId()).orElseThrow();
        assertThat(reloaded.getConfirmedAt()).isCloseTo(originalConfirmedAt, within(1, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("사전 조회한 알림 목록에 없는, 그 사이 새로 생긴 알림은 확인 처리되지 않는다")
    void 벌크_확인_처리_사전_조회_이후_추가된_알림_제외() {
        // given
        User user = saveUser("me@test.com", "나");
        Notification target = saveNotification(user, "사전 조회된 알림", LocalDateTime.now());
        List<UUID> targetIds = List.of(target.getId());

        Notification arrivedLater = saveNotification(user, "그 사이 도착한 새 알림", LocalDateTime.now());

        // when
        int updatedCount = notificationRepository.confirmAllByUserId(user.getId(), targetIds, LocalDateTime.now());

        // then
        assertThat(updatedCount).isEqualTo(1);

        Notification reloadedTarget = notificationRepository.findById(target.getId()).orElseThrow();
        Notification reloadedLater = notificationRepository.findById(arrivedLater.getId()).orElseThrow();
        assertThat(reloadedTarget.isConfirmed()).isTrue();
        assertThat(reloadedLater.isConfirmed()).isFalse();
    }
}
