package com.codeit.sb13.monew.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserAutoDeleteScheduler {
  private final UserService userService;

  @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
  public void userAutoDeleteScheduler() {
    userService.autoDeleteExpiredUsers();
  }
}
