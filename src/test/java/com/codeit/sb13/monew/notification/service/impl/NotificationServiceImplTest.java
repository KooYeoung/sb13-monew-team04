package com.codeit.sb13.monew.notification.service.impl;

import com.codeit.sb13.monew.global.dto.CursorPageResponseDto;
import com.codeit.sb13.monew.global.exception.notification.NotificationNotFoundException;
import com.codeit.sb13.monew.global.exception.user.UserNotFoundException;
import com.codeit.sb13.monew.notification.domain.Notification;
import com.codeit.sb13.monew.notification.domain.ResourceType;
import com.codeit.sb13.monew.notification.mapper.NotificationMapper;
import com.codeit.sb13.monew.notification.repository.NotificationRepository;
import com.codeit.sb13.monew.notification.repository.dto.NotificationFindCondition;
import com.codeit.sb13.monew.notification.service.dto.ArticlesForInterestDto;
import com.codeit.sb13.monew.notification.service.dto.CommentLikedDto;
import com.codeit.sb13.monew.notification.service.dto.NotificationFindDto;
import com.codeit.sb13.monew.notification.service.dto.NotificationResult;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    NotificationRepository notificationRepository;

    @InjectMocks
    NotificationServiceImpl notificationServiceImpl;

    @Captor
    ArgumentCaptor<List<Notification>> notificationsCaptor;

    @Mock
    UserRepository userRepository;

    @Mock
    NotificationMapper mapper;

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

    @Nested
    @DisplayName("confirmNotification")
    class ConfirmNotification {

        @Test
        @DisplayName("본인 알림을 확인하면 confirmed가 true로 바뀌고 결과가 반환된다.")
        void 본인_알림_확인_성공() {
            // given
            UUID userId = UUID.randomUUID();
            User user = User.builder().email("test@test.com").nickname("테스트").password("pw").build();
            ReflectionTestUtils.setField(user, "id", userId);

            UUID notificationId = UUID.randomUUID();
            Notification notification = Notification.create(user, "내용", UUID.randomUUID(), ResourceType.COMMENT);

            when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
            when(userRepository.existsById(userId)).thenReturn(true);

            NotificationResult expectedResult = new NotificationResult(
                    notification.getId(), userId, "내용", notification.getResourceId(),
                    ResourceType.COMMENT, true, LocalDateTime.now(), LocalDateTime.now()
            );
            when(mapper.toResult(notification)).thenReturn(expectedResult);

            // when
            NotificationResult result = notificationServiceImpl.confirmNotification(notificationId, userId);

            // then
            assertThat(notification.isConfirmed()).isTrue();
            assertThat(result).isEqualTo(expectedResult);
            verify(notificationRepository).save(notification);
        }

        @Test
        @DisplayName("존재하지 않는 notificationId면 NotificationNotFoundException이 발생한다.")
        void 알림_없으면_예외() {
            // given
            UUID notificationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(userRepository.existsById(userId)).thenReturn(true);
            when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> notificationServiceImpl.confirmNotification(notificationId, userId))
                    .isInstanceOf(NotificationNotFoundException.class);
        }

        @Test
        @DisplayName("존재하지 않는 요청자면 UserNotFoundException이 발생한다.")
        void 요청자_없으면_예외() {
            // given
            UUID notificationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            when(userRepository.existsById(userId)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> notificationServiceImpl.confirmNotification(notificationId, userId))
                    .isInstanceOf(UserNotFoundException.class);

            verify(notificationRepository, never()).findById(any());
        }

        @Test
        @DisplayName("본인 알림이 아니면 NotificationNotFoundException이 발생한다.")
        void 본인_알림_아니면_예외() {
            // given
            UUID ownerId = UUID.randomUUID();
            UUID otherId = UUID.randomUUID();

            User owner = User.builder().email("owner@naver.com").nickname("진짜").password("pw").build();
            ReflectionTestUtils.setField(owner, "id", ownerId);

            User other = User.builder().email("other@naver.com").nickname("가짜").password("pw").build();
            ReflectionTestUtils.setField(other, "id", otherId);

            UUID notificationId = UUID.randomUUID();
            Notification notification = Notification.create(owner, "내용", UUID.randomUUID(), ResourceType.COMMENT);

            when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
            when(userRepository.existsById(otherId)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> notificationServiceImpl.confirmNotification(notificationId, otherId))
                    .isInstanceOf(NotificationNotFoundException.class);

            verify(notificationRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("confirmAllNotifications")
    class confirmAllNotifications {

        @Test
        @DisplayName("요청자의 미확인 알림이 모두 확인 처리된다.")
        void 전체_확인_성공() {
            // given
            UUID userId = UUID.randomUUID();
            User user = User.builder().email("test@test.com").nickname("테스트").password("pw").build();
            ReflectionTestUtils.setField(user, "id", userId);

            Notification notification1 = Notification.create(user, "알림1", UUID.randomUUID(), ResourceType.COMMENT);
            Notification notification2 = Notification.create(user, "알림2", UUID.randomUUID(), ResourceType.INTEREST);
            List<Notification> notifications = List.of(notification1, notification2);

            when(userRepository.existsById(userId)).thenReturn(true);
            when(notificationRepository.findByUser_IdAndConfirmedFalse(userId)).thenReturn(notifications);

            NotificationResult notificationResult = new NotificationResult(
                    UUID.randomUUID(), userId, "아무 내용", UUID.randomUUID(),
                    ResourceType.COMMENT, true, null, null
            );
            when(mapper.toResult(any(Notification.class))).thenReturn(notificationResult);

            // when
            List<NotificationResult> result = notificationServiceImpl.confirmAllNotifications(userId);

            // then
            assertThat(notification1.isConfirmed()).isTrue();
            assertThat(notification2.isConfirmed()).isTrue();

            verify(notificationRepository).saveAll(notifications);
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("존재하지 않는 요청자면 UserNotFoundException이 발생하고 알림 조회는 시도되지 않는다.")
        void 요청자_없으면_예외() {
            // given
            UUID userId = UUID.randomUUID();
            when(userRepository.existsById(userId)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> notificationServiceImpl.confirmAllNotifications(userId))
                    .isInstanceOf(UserNotFoundException.class);

            verify(notificationRepository, never()).findByUser_IdAndConfirmedFalse(any());
        }
    }

    @Nested
    @DisplayName("findAllNotifications")
    class FindAllNotifications {

        @Test
        @DisplayName("다음 페이지가 있으면 limit만큼 잘라서 반환하고 hasNext가 true다.")
        void 다음_페이지_있으면_hasNext_true() {
            // given
            UUID userId = UUID.randomUUID();
            User user = User.builder().email("test@test.com").nickname("테스트").password("pw").build();
            ReflectionTestUtils.setField(user, "id", userId);

            int limit = 2;
            Notification n1 = Notification.create(user, "알림1", UUID.randomUUID(), ResourceType.COMMENT);
            Notification n2 = Notification.create(user, "알림2", UUID.randomUUID(), ResourceType.COMMENT);
            Notification n3 = Notification.create(user, "알림3", UUID.randomUUID(), ResourceType.COMMENT);
            ReflectionTestUtils.setField(n1, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(n2, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(n3, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(n1, "createdAt", LocalDateTime.now());
            ReflectionTestUtils.setField(n2, "createdAt", LocalDateTime.now().minusMinutes(1));
            ReflectionTestUtils.setField(n3, "createdAt", LocalDateTime.now().minusMinutes(2));

            NotificationFindDto request = new NotificationFindDto(null, null, limit, userId);

            when(userRepository.existsById(userId)).thenReturn(true);
            when(notificationRepository.findUnconfirmedByUserWithCursor(
                    new NotificationFindCondition(userId, null, null, limit + 1)))
                    .thenReturn(List.of(n1, n2, n3));
            when(notificationRepository.countByUser_IdAndConfirmedFalse(userId)).thenReturn(5L);
            when(mapper.toResult(any(Notification.class))).thenAnswer(invocation -> {
                Notification n = invocation.getArgument(0);
                return new NotificationResult(n.getId(), userId, n.getContent(), n.getResourceId(),
                        n.getResourceType(), n.isConfirmed(), n.getCreatedAt(), n.getUpdatedAt());
            });

            // when
            CursorPageResponseDto<NotificationResult> result = notificationServiceImpl.findAllNotifications(request);

            // then
            assertThat(result.content()).hasSize(2);
            assertThat(result.hasNext()).isTrue();
            assertThat(result.totalElements()).isEqualTo(5L);
            assertThat(result.nextCursor()).isEqualTo(n2.getId().toString());
            assertThat(result.nextAfter()).isEqualTo(n2.getCreatedAt().toString());
        }

        @Test
        @DisplayName("다음 페이지가 없으면 조회된 만큼만 반환하고 hasNext가 false다.")
        void 다음_페이지_없으면_hasNext_false() {
            // given
            UUID userId = UUID.randomUUID();
            User user = User.builder().email("test@test.com").nickname("테스트").password("pw").build();
            ReflectionTestUtils.setField(user, "id", userId);

            int limit = 10;
            Notification n1 = Notification.create(user, "알림1", UUID.randomUUID(), ResourceType.COMMENT);
            ReflectionTestUtils.setField(n1, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(n1, "createdAt", LocalDateTime.now());

            NotificationFindDto request = new NotificationFindDto(null, null, limit, userId);

            when(userRepository.existsById(userId)).thenReturn(true);
            when(notificationRepository.findUnconfirmedByUserWithCursor(
                    new NotificationFindCondition(userId, null, null, limit + 1)))
                    .thenReturn(List.of(n1));
            when(notificationRepository.countByUser_IdAndConfirmedFalse(userId)).thenReturn(1L);
            when(mapper.toResult(n1)).thenReturn(new NotificationResult(
                    n1.getId(), userId, n1.getContent(), n1.getResourceId(),
                    n1.getResourceType(), n1.isConfirmed(), n1.getCreatedAt(), n1.getUpdatedAt()));

            // when
            CursorPageResponseDto<NotificationResult> result = notificationServiceImpl.findAllNotifications(request);

            // then
            assertThat(result.content()).hasSize(1);
            assertThat(result.hasNext()).isFalse();
            assertThat(result.nextCursor()).isNull();
            assertThat(result.nextAfter()).isNull();
        }

        @Test
        @DisplayName("존재하지 않는 요청자면 UserNotFoundException이 발생한다.")
        void 요청자_없으면_예외() {
            // given
            UUID userId = UUID.randomUUID();
            NotificationFindDto request = new NotificationFindDto(null, null, 10, userId);
            when(userRepository.existsById(userId)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> notificationServiceImpl.findAllNotifications(request))
                    .isInstanceOf(UserNotFoundException.class);

            verify(notificationRepository, never()).findUnconfirmedByUserWithCursor(any(NotificationFindCondition.class));
        }

    }
}