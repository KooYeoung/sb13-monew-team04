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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
}