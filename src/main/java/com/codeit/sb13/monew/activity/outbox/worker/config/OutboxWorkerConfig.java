package com.codeit.sb13.monew.activity.outbox.worker.config;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Outbox worker 설정 binding과 claim heartbeat 실행 자원을 구성한다.
 */
@Configuration
@EnableConfigurationProperties(OutboxWorkerProperties.class)
public class OutboxWorkerConfig {

    /**
     * claim heartbeat 전용 단일 daemon scheduler를 생성한다.
     *
     * <p>한 애플리케이션 인스턴스의 fixed-delay worker는 동시에 하나의 batch만
     * 처리하므로 heartbeat thread도 하나만 사용한다.</p>
     *
     * @return 애플리케이션 종료 시 shutdown되는 heartbeat executor
     */
    @Bean(name = "outboxClaimHeartbeatExecutor", destroyMethod = "shutdown")
    public ScheduledExecutorService outboxClaimHeartbeatExecutor() {
        return Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "outbox-claim-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }
}
