package com.codeit.sb13.monew.article.schedule.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 기사 수집 스케줄러({@code ArticleCollectJobService})의 활성화 여부와 실행 주기를 담는다.
 *
 * <p>{@link ArticleScheduleProperties}가 기사 백업 스케줄 설정을 담는 것과 같은 방식으로,
 * {@code monew.collect.schedule} 프리픽스 아래 설정을 바인딩한다.</p>
 *
 * @param enabled 기사 수집 스케줄러 활성화 여부
 * @param cron 기사 수집을 실행할 cron 표현식
 */
@ConfigurationProperties(prefix = "monew.collect.schedule")
public record ArticleCollectScheduleProperties(
        boolean enabled,
        String cron
) {
}
