package com.codeit.sb13.monew.user.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.sb13.monew.global.service.AdvisoryLockService;
import com.codeit.sb13.monew.user.service.config.UserScheduleProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserAutoDeleteSchedulerTest {

  @Mock
  UserService userService;
  @Mock
  AdvisoryLockService advisoryLockService;
  @Mock
  UserScheduleProperties props;

  @InjectMocks
  UserAutoDeleteScheduler userAutoDeleteScheduler;

  @Test
  @DisplayName("락 획득에 성공하면 배치가 실행된다")
  void 락_획득에_성공하면_배치가_실행된다() {
    // given
    when(props.enabled()).thenReturn(true);
    when(advisoryLockService.executeWithLock(eq("user-auto-delete"), any(Runnable.class)))
        .thenAnswer(invocation -> {
          Runnable task = invocation.getArgument(1);
          task.run();
          return true;
        });
    // when
    userAutoDeleteScheduler.userAutoDeleteScheduler();

    // then
    verify(advisoryLockService).executeWithLock(eq("user-auto-delete"), any(Runnable.class));
    verify(userService).autoDeleteExpiredUsers();
  }

  @Test
  @DisplayName("락 획득에 실패하면 배치가 실행되지 않는다")
  void 락_획득에_실페하면_배치가_실행되지_않는다() {
    // given
    when(props.enabled()).thenReturn(true);
    when(advisoryLockService.executeWithLock(eq("user-auto-delete"), any(Runnable.class)))
        .thenReturn(false);

    // when & then
    assertThatCode(() -> userAutoDeleteScheduler.userAutoDeleteScheduler())
        .doesNotThrowAnyException();
    verify(advisoryLockService).executeWithLock(eq("user-auto-delete"), any(Runnable.class));
    verify(userService, never()).autoDeleteExpiredUsers();

  }



  @Test
  @DisplayName("스케줄러가 비활성화되어 있으면 배치가 실행되지 않는다")
  void 스케줄러가_비활성화되어_있으면_배치가_실행되지_않는다() {
    // given
    when(props.enabled()).thenReturn(false);

    // when
    userAutoDeleteScheduler.userAutoDeleteScheduler();

    // then
    verify(advisoryLockService, never()).executeWithLock(any(), any());
  }

}