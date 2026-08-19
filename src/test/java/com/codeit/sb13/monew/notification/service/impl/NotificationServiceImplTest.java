package com.codeit.sb13.monew.notification.service.impl;

import com.codeit.sb13.monew.notification.domain.Notification;
import com.codeit.sb13.monew.notification.domain.ResourceType;
import com.codeit.sb13.monew.notification.repository.NotificationRepository;
import com.codeit.sb13.monew.notification.service.dto.ArticlesForInterestDto;
import com.codeit.sb13.monew.notification.service.dto.CommentLikedDto;
import com.codeit.sb13.monew.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    NotificationRepository notificationRepository;

    @InjectMocks
    NotificationServiceImpl notificationServiceImpl;

    @Captor
    ArgumentCaptor<List<Notification>> notificationsCaptor;

    @Nested
    @DisplayName("notifyArticlesForInterest")
    class NotifyArticlesForInterest {

        @Test
        @DisplayName("구독자 수만큼 관심사 알림이 생성된다.")
        void 구독자수만큼_관심사_알림_생성() {
            // given
            UUID resourceId = UUID.randomUUID();
            User user1 = User.builder().email("aaa@naver.com").nickname("유저1").password("pw").build();
            User user2 = User.builder().email("bbb@naver.com").nickname("유저2").password("pw").build();
            ArticlesForInterestDto request = new ArticlesForInterestDto(List.of(user1, user2), resourceId, "백엔드", 3);

            // when
            notificationServiceImpl.notifyArticlesForInterest(request);

            // then
            verify(notificationRepository).saveAll(notificationsCaptor.capture());
            List<Notification> saved = notificationsCaptor.getValue();

            assertThat(saved).hasSize(2);
            assertThat(saved)
                    .extracting(Notification::getUser)
                    .containsExactly(user1, user2);

            assertThat(saved).allSatisfy(n -> {
                assertThat(n.getContent()).isEqualTo("[백엔드]와 관련된 기사가 3건 등록되었습니다.");
                assertThat(n.getResourceId()).isEqualTo(resourceId);
                assertThat(n.getResourceType()).isEqualTo(ResourceType.INTEREST);
            });
        }

        @Test
        @DisplayName("구독자가 없으면 알림을 생성하지 않는다.")
        void 구독자_없으면_알림_미생성() {
            // given
            ArticlesForInterestDto request = new ArticlesForInterestDto(List.of(), UUID.randomUUID(), "백엔드", 3);

            // when
            notificationServiceImpl.notifyArticlesForInterest(request);

            // then
            verify(notificationRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("구독자가 null이면 알림을 생성하지 않는다.")
        void 구독자_null이면_알림_미생성() {
            // given
            ArticlesForInterestDto request = new ArticlesForInterestDto(null, UUID.randomUUID(), "백엔드", 3);

            // when
            notificationServiceImpl.notifyArticlesForInterest(request);

            // then
            verify(notificationRepository, never()).saveAll(any());
        }
    }

    @Nested
    @DisplayName("notifyCommentLiked")
    class NotifyCommentLiked {

        @Test
        @DisplayName("좋아요를 누르면 댓글 작성자에게 알림이 생성된다.")
        void 댓글_좋아요_알림_생성() {
            // given
            User sender = User.builder().email("sender@naver.com").nickname("좋아요보낸사람").password("pw").build();
            User recipient = User.builder().email("recipient@naver.com").nickname("작성자").password("pw").build();
            UUID resourceId = UUID.randomUUID();
            CommentLikedDto request = new CommentLikedDto(sender, recipient, resourceId);

            // when
            notificationServiceImpl.notifyCommentLiked(request);

            // then
            verify(notificationRepository).saveAll(notificationsCaptor.capture());
            List<Notification> saved = notificationsCaptor.getValue();

            assertThat(saved).hasSize(1);
            Notification notification = saved.get(0);
            assertThat(notification.getUser()).isEqualTo(recipient);
            assertThat(notification.getContent()).isEqualTo("[좋아요보낸사람]님이 나의 댓글을 좋아합니다.");
            assertThat(notification.getResourceId()).isEqualTo(resourceId);
            assertThat(notification.getResourceType()).isEqualTo(ResourceType.COMMENT);

        }

        @Test
        @DisplayName("좋아요를 보낸 사람이 없으면 알림을 생성하지 않는다.")
        void sender_없으면_알림_미생성() {
            // given
            User recipient = User.builder().email("recipient@naver.com").nickname("작성자").password("pw").build();
            CommentLikedDto request = new CommentLikedDto(null, recipient, UUID.randomUUID());

            // when
            notificationServiceImpl.notifyCommentLiked(request);

            // then
            verify(notificationRepository, never()).saveAll(any());
        }
    }
}