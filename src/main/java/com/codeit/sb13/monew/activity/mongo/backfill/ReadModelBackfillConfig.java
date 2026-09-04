package com.codeit.sb13.monew.activity.mongo.backfill;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 초기 투영 설정 binding과 heartbeat 실행 자원을 구성한다. */
@Configuration
@EnableConfigurationProperties(ReadModelBackfillProperties.class)
public class ReadModelBackfillConfig {

    @Bean(name = "readModelBackfillHeartbeatExecutor", destroyMethod = "shutdown")
    public ScheduledExecutorService readModelBackfillHeartbeatExecutor() {
        return Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "read-model-backfill-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }
}
