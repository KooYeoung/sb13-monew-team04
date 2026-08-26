package com.codeit.sb13.monew.user.service;

import com.codeit.sb13.monew.global.service.AdvisoryLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserAutoDeleteScheduler {

  private static final String LOCK_KEY = "user-auto-delete";

  private final UserService userService;
  private final AdvisoryLockService advisoryLockService;

  @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
  public void userAutoDeleteScheduler() {

    boolean executed = advisoryLockService.executeWithLock(LOCK_KEY, userService::autoDeleteExpiredUsers);

    if (!executed) {
      log.warn("이미 다른 인스턴스가 사용자 자동 삭제를 실행 중입니다.");
      return;
    }
    log.info("사용자 자동 삭제 스케줄러 작업을 완료했습니다.");

  }
}
