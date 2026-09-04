package com.codeit.sb13.monew.activity.service.config;

import com.codeit.sb13.monew.activity.service.UserActivityReadSourceType;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 사용자 활동내역 조회 트래픽이 사용할 저장소를 선택한다.
 *
 * @param readSource 조회 source. 설정하지 않으면 RDB를 사용한다
 */
@ConfigurationProperties(prefix = "monew.activity")
public record UserActivityReadProperties(
        UserActivityReadSourceType readSource
) {
    public UserActivityReadProperties {
        readSource = readSource == null ? UserActivityReadSourceType.RDB : readSource;
    }
}
