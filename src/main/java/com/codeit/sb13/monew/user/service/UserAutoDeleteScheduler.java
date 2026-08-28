package com.codeit.sb13.monew.user.service;

import com.codeit.sb13.monew.global.service.AdvisoryLockService;
import com.codeit.sb13.monew.user.service.config.UserScheduleProperties;
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
  private final UserScheduleProperties props;

  @Scheduled(cron = "${monew.user.schedule.cron}", zone = "Asia/Seoul")
  public void userAutoDeleteScheduler() {
    if (!props.enabled()) {
      log.info("사용자 자동 삭제 스케줄러가 비활성화되어 작업을 건너뜁니다.");
      return;
    }

    boolean executed = advisoryLockService.executeWithLock(LOCK_KEY, userService::autoDeleteExpiredUsers);

    if (!executed) {
      log.info("이미 다른 인스턴스가 사용자 자동 삭제를 실행 중입니다.");
      return;
    }
    log.info("사용자 자동 삭제 스케줄러 작업을 완료했습니다.");
  }
}